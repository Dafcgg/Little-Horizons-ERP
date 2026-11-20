package com.littlehorizons.erp.dao.jdbc;

import com.littlehorizons.erp.dao.FinanceDao;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.util.*;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Handles balances and payments and delegates invoice persistence on the same connection. */
public final class JdbcFinanceDao implements FinanceDao {
  private final JdbcInvoiceQueries invoiceQueries = new JdbcInvoiceQueries();

  public long createConcept(
      Connection c, String code, String name, BigDecimal amount, BigDecimal rate)
      throws SQLException {
    return Jdbc.insert(
        c,
        """
INSERT INTO conceptos_pago(codigo_concepto,nombre_concepto,monto_base,tasa_iva) VALUES(?,?,?,?)
""",
        code,
        name,
        amount,
        rate);
  }

  public List<String> concepts(Connection c) throws SQLException {
    return Jdbc.query(
        c,
        """
SELECT id_concepto,nombre_concepto,monto_base,tasa_iva FROM conceptos_pago ORDER BY id_concepto
""",
        r ->
            r.getLong(1)
                + " | "
                + r.getString(2)
                + " | COP "
                + r.getBigDecimal(3)
                + " | IVA "
                + r.getBigDecimal(4));
  }

  public void setFee(Connection c, long student, long concept, BigDecimal amount)
      throws SQLException {
    Jdbc.update(
        c,
        """
INSERT INTO tarifas_estudiante(id_estudiante,id_concepto,monto) VALUES(?,?,?) ON DUPLICATE KEY
UPDATE monto=?
""",
        student,
        concept,
        amount,
        amount);
  }

  public Optional<FeeQuote> quote(Connection c, long student, long concept) throws SQLException {
    return Jdbc.query(
            c,
            """
SELECT COALESCE(t.monto,p.monto_base),p.tasa_iva FROM conceptos_pago p LEFT JOIN tarifas_estudiante
t ON t.id_concepto=p.id_concepto AND t.id_estudiante=? WHERE p.id_concepto=? AND p.esta_activo=TRUE
""",
            r -> new FeeQuote(r.getBigDecimal(1), r.getBigDecimal(2)),
            student,
            concept)
        .stream()
        .findFirst();
  }

  public long createObligation(
      Connection c,
      long student,
      long concept,
      LocalDate due,
      String reference,
      BigDecimal amount,
      BigDecimal taxRate)
      throws SQLException {
    return Jdbc.insert(
        c,
        """
INSERT INTO obligaciones(id_estudiante,id_concepto,referencia,fecha_vencimiento,monto,tasa_iva)
VALUES(?,?,?,?,?,?)
""",
        student,
        concept,
        reference,
        due,
        amount,
        taxRate);
  }

  public Optional<Obligation> lockObligation(Connection c, long id) throws SQLException {
    var rows =
        Jdbc.query(
            c,
            """
            SELECT o.*,p.nombre_concepto FROM obligaciones o JOIN conceptos_pago p ON
            p.id_concepto=o.id_concepto WHERE o.id_obligacion=? FOR UPDATE
            """,
            r ->
                new Obligation(
                    r.getLong("id_obligacion"),
                    r.getLong("id_estudiante"),
                    r.getLong("id_concepto"),
                    r.getString("nombre_concepto"),
                    r.getBigDecimal("monto"),
                    r.getBigDecimal("tasa_iva"),
                    BigDecimal.ZERO),
            id);
    if (rows.isEmpty()) return Optional.empty();
    Obligation o = rows.get(0);
    BigDecimal paid =
        Jdbc.query(
                c,
                """
SELECT COALESCE(SUM(monto_pagado),0) FROM pagos WHERE id_obligacion=? AND estado_pago='Confirmado'
""",
                r -> r.getBigDecimal(1),
                id)
            .get(0);
    return Optional.of(
        new Obligation(
            o.id(), o.studentId(), o.conceptId(), o.description(), o.amount(), o.taxRate(), paid));
  }

  public Optional<StoredPayment> findPayment(Connection c, String receipt) throws SQLException {
    return Jdbc.query(
            c,
            """
SELECT p.*,f.id_factura FROM pagos p JOIN facturas f ON f.id_pago=p.id_pago WHERE numero_recibo=?
""",
            r -> {
              String methodValue = r.getString("metodo_pago");
              PaymentMethod method =
                  Arrays.stream(PaymentMethod.values())
                      .filter(v -> v.databaseValue().equals(methodValue))
                      .findFirst()
                      .orElseThrow();
              return new StoredPayment(
                  r.getLong("id_pago"),
                  r.getLong("id_factura"),
                  new PaymentRequest(
                      r.getLong("id_obligacion"),
                      r.getLong("id_acudiente"),
                      r.getBigDecimal("monto_pagado"),
                      r.getObject("fecha_pago", LocalDate.class),
                      method,
                      r.getString("numero_recibo")));
            },
            receipt)
        .stream()
        .findFirst();
  }

  public Optional<InvoiceCustomer> responsible(Connection c, long student, long guardian)
      throws SQLException {
    return Jdbc.query(
            c,
            """
SELECT a.numero_documento,a.primer_nombre,a.segundo_nombre,
       a.primer_apellido,a.segundo_apellido,a.direccion
FROM acudientes a JOIN estudiante_acudiente ea ON ea.id_acudiente=a.id_acudiente WHERE
ea.id_estudiante=? AND a.id_acudiente=? AND ea.es_responsable_financiero=TRUE AND a.esta_activo=TRUE
""",
            r ->
                new InvoiceCustomer(
                    Stream.of(
                            r.getString("primer_nombre"), r.getString("segundo_nombre"),
                            r.getString("primer_apellido"), r.getString("segundo_apellido"))
                        .filter(name -> name != null && !name.isBlank())
                        .map(String::strip)
                        .collect(Collectors.joining(" ")),
                    r.getString("numero_documento"),
                    r.getString("direccion")),
            student,
            guardian)
        .stream()
        .findFirst();
  }

  public long insertPayment(Connection c, Obligation o, PaymentRequest p) throws SQLException {
    return Jdbc.insert(
        c,
        """
INSERT INTO
pagos(id_estudiante,id_acudiente,id_concepto,id_obligacion,monto_pagado,fecha_pago,metodo_pago,numero_recibo)
VALUES(?,?,?,?,?,?,?,?)
""",
        o.studentId(),
        p.guardianId(),
        o.conceptId(),
        o.id(),
        p.amount(),
        p.date(),
        p.method().databaseValue(),
        p.receipt());
  }

  public long insertInvoice(
      Connection c,
      long payment,
      PaymentRequest request,
      InvoiceCustomer customer,
      SchoolInformation s,
      BigDecimal subtotal,
      BigDecimal tax,
      LocalDateTime issuedAt)
      throws SQLException {
    return invoiceQueries.insertInvoice(c, payment, request, customer, s, subtotal, tax, issuedAt);
  }

  public void identifyInvoice(Connection c, long id, String number, String cufe)
      throws SQLException {
    invoiceQueries.identifyInvoice(c, id, number, cufe);
  }

  public void insertLine(Connection c, long invoice, InvoiceLine line) throws SQLException {
    invoiceQueries.insertLine(c, invoice, line);
  }

  public Optional<Invoice> invoice(Connection c, long id) throws SQLException {
    return invoiceQueries.invoice(c, id);
  }

  public int fileStatus(Connection c, long id, String status, String path) throws SQLException {
    return invoiceQueries.fileStatus(c, id, status, path);
  }

  public List<Balance> balances(Connection c, LocalDate cutoff) throws SQLException {
    return Jdbc.query(
        c,
        """
SELECT
o.id_obligacion,o.id_estudiante,p.nombre_concepto,o.fecha_vencimiento,o.monto-COALESCE(SUM(pg.monto_pagado),0)
saldo FROM obligaciones o JOIN conceptos_pago p ON p.id_concepto=o.id_concepto LEFT JOIN pagos pg ON
pg.id_obligacion=o.id_obligacion AND pg.estado_pago='Confirmado' WHERE o.estado='Activa' AND
o.fecha_vencimiento<? GROUP BY
o.id_obligacion,o.id_estudiante,p.nombre_concepto,o.fecha_vencimiento,o.monto HAVING saldo>0 ORDER
BY o.fecha_vencimiento
""",
        r ->
            new Balance(
                r.getLong(1),
                r.getLong(2),
                r.getString(3),
                r.getObject(4, LocalDate.class),
                r.getBigDecimal(5)),
        cutoff);
  }

  public List<String> history(Connection c, long id, boolean guardian) throws SQLException {
    String sql;
    if (guardian) {
      sql =
          """
SELECT p.id_pago,p.id_estudiante,p.fecha_pago,p.monto_pagado,p.numero_recibo,p.estado_pago
FROM pagos p
WHERE EXISTS(SELECT 1 FROM acudientes a WHERE a.id_acudiente=? AND
    (p.id_acudiente=a.id_acudiente OR EXISTS(SELECT 1 FROM estudiante_acudiente ea
        WHERE ea.id_estudiante=p.id_estudiante AND ea.id_acudiente=a.id_acudiente)))
ORDER BY p.fecha_pago,p.id_pago
""";
    } else {
      sql =
          """
SELECT p.id_pago,p.id_estudiante,p.fecha_pago,p.monto_pagado,p.numero_recibo,p.estado_pago
FROM pagos p WHERE p.id_estudiante=? ORDER BY p.fecha_pago,p.id_pago
""";
    }
    return Jdbc.query(
        c,
        sql,
        r ->
            r.getLong(1)
                + " | Estudiante "
                + r.getLong(2)
                + " | "
                + r.getObject(3, LocalDate.class)
                + " | COP "
                + r.getBigDecimal(4)
                + " | "
                + r.getString(5)
                + " | "
                + r.getString(6),
        id);
  }

  private PaymentConcept concept(java.sql.ResultSet r) throws SQLException {
    return new PaymentConcept(
        r.getLong("id_concepto"),
        r.getString("codigo_concepto"),
        r.getString("nombre_concepto"),
        r.getBigDecimal("monto_base"),
        r.getBigDecimal("tasa_iva"),
        r.getBoolean("esta_activo"));
  }

  public List<PaymentConcept> conceptEntries(Connection c) throws SQLException {
    return Jdbc.query(c, "SELECT * FROM conceptos_pago ORDER BY id_concepto", this::concept);
  }

  public Optional<PaymentConcept> concept(Connection c, long id, boolean lock) throws SQLException {
    String sql;
    if (lock) {
      sql = "SELECT * FROM conceptos_pago WHERE id_concepto=? FOR UPDATE";
    } else {
      sql = "SELECT * FROM conceptos_pago WHERE id_concepto=?";
    }
    return Jdbc.query(c, sql, this::concept, id).stream().findFirst();
  }

  public int updateConcept(Connection c, PaymentConcept v) throws SQLException {
    return Jdbc.update(
        c,
        """
UPDATE conceptos_pago SET codigo_concepto=?,nombre_concepto=?,monto_base=?,tasa_iva=?,esta_activo=?
WHERE id_concepto=?
""",
        v.code(),
        v.name(),
        v.amount(),
        v.taxRate(),
        v.active(),
        v.id());
  }

  public List<StudentFee> fees(Connection c, long student) throws SQLException {
    return Jdbc.query(
        c,
        """
SELECT id_estudiante,id_concepto,monto FROM tarifas_estudiante WHERE id_estudiante=? ORDER BY
id_concepto
""",
        r -> new StudentFee(r.getLong(1), r.getLong(2), r.getBigDecimal(3)),
        student);
  }

  public int deleteFee(Connection c, long student, long concept) throws SQLException {
    return Jdbc.update(
        c,
        "DELETE FROM tarifas_estudiante WHERE id_estudiante=? AND id_concepto=?",
        student,
        concept);
  }

  private ObligationEntry obligation(java.sql.ResultSet r) throws SQLException {
    return new ObligationEntry(
        r.getLong("id_obligacion"),
        r.getLong("id_estudiante"),
        r.getLong("id_concepto"),
        r.getString("referencia"),
        r.getObject("fecha_vencimiento", LocalDate.class),
        r.getBigDecimal("monto"),
        r.getBigDecimal("tasa_iva"),
        r.getString("estado"));
  }

  public List<ObligationEntry> obligations(Connection c) throws SQLException {
    return Jdbc.query(c, "SELECT * FROM obligaciones ORDER BY id_obligacion", this::obligation);
  }

  public Optional<ObligationEntry> obligation(Connection c, long id, boolean lock)
      throws SQLException {
    String sql;
    if (lock) {
      sql = "SELECT * FROM obligaciones WHERE id_obligacion=? FOR UPDATE";
    } else {
      sql = "SELECT * FROM obligaciones WHERE id_obligacion=?";
    }
    return Jdbc.query(c, sql, this::obligation, id).stream().findFirst();
  }

  public int updateObligation(Connection c, ObligationEntry v) throws SQLException {
    return Jdbc.update(
        c,
        """
UPDATE obligaciones SET referencia=?,fecha_vencimiento=?,monto=?,estado=? WHERE id_obligacion=?
""",
        v.reference(),
        v.dueDate(),
        v.amount(),
        v.status(),
        v.id());
  }

  private PaymentEntry payment(java.sql.ResultSet r) throws SQLException {
    String value = r.getString("metodo_pago");
    PaymentMethod method =
        Arrays.stream(PaymentMethod.values())
            .filter(v -> v.databaseValue().equals(value))
            .findFirst()
            .orElseThrow();
    Number original = (Number) r.getObject("id_pago_original");
    return new PaymentEntry(
        r.getLong("id_pago"),
        r.getLong("id_estudiante"),
        new PaymentRequest(
            r.getLong("id_obligacion"),
            r.getLong("id_acudiente"),
            r.getBigDecimal("monto_pagado"),
            r.getObject("fecha_pago", LocalDate.class),
            method,
            r.getString("numero_recibo")),
        r.getString("estado_pago"),
        r.getString("motivo_anulacion"),
        original == null ? null : original.longValue());
  }

  public List<PaymentEntry> payments(Connection c) throws SQLException {
    return Jdbc.query(c, "SELECT * FROM pagos ORDER BY id_pago", this::payment);
  }

  public Optional<PaymentEntry> payment(Connection c, long id, boolean lock) throws SQLException {
    String sql;
    if (lock) {
      sql = "SELECT * FROM pagos WHERE id_pago=? FOR UPDATE";
    } else {
      sql = "SELECT * FROM pagos WHERE id_pago=?";
    }
    return Jdbc.query(c, sql, this::payment, id).stream().findFirst();
  }

  public Optional<Long> invoiceId(Connection c, long payment) throws SQLException {
    return invoiceQueries.invoiceId(c, payment);
  }

  public int voidPayment(Connection c, long id, String reason) throws SQLException {
    int changed =
        Jdbc.update(
            c,
            """
            UPDATE pagos SET estado_pago='Anulado',motivo_anulacion=? WHERE id_pago=? AND
            estado_pago='Confirmado'
            """,
            reason,
            id);
    if (changed > 0) invoiceQueries.cancelForPayment(c, id, reason);
    return changed;
  }

  public void markReplacement(Connection c, long replacement, long original) throws SQLException {
    Jdbc.update(c, "UPDATE pagos SET id_pago_original=? WHERE id_pago=?", original, replacement);
  }

  public List<Invoice> invoices(Connection c) throws SQLException {
    return invoiceQueries.invoices(c);
  }

  public boolean activeStudent(Connection c, long student) throws SQLException {
    return !Jdbc.query(
            c,
            """
SELECT id_estudiante FROM estudiantes WHERE id_estudiante=? AND estado='Activo' FOR UPDATE
""",
            r -> r.getLong(1),
            student)
        .isEmpty();
  }

  public boolean markFileGenerated(Connection c, long id, String path, String expectedState)
      throws SQLException {
    return invoiceQueries.markFileGenerated(c, id, path, expectedState);
  }
}
