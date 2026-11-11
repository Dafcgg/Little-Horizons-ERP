package com.littlehorizons.erp.dao.jdbc;

import com.littlehorizons.erp.dao.FinanceDao;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.util.*;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;

public final class JdbcFinanceDao implements FinanceDao {
  public long createConcept(
      Connection c, String code, String name, BigDecimal amount, BigDecimal rate)
      throws SQLException {
    return Jdbc.insert(
        c,
        "INSERT INTO conceptos_pago(codigo_concepto,nombre_concepto,monto_base,tasa_iva)"
            + " VALUES(?,?,?,?)",
        code,
        name,
        amount,
        rate);
  }

  public List<String> concepts(Connection c) throws SQLException {
    return Jdbc.query(
        c,
        "SELECT id_concepto,nombre_concepto,monto_base,tasa_iva FROM conceptos_pago ORDER BY"
            + " id_concepto",
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
        "INSERT INTO tarifas_estudiante(id_estudiante,id_concepto,monto) VALUES(?,?,?) ON DUPLICATE"
            + " KEY UPDATE monto=?",
        student,
        concept,
        amount,
        amount);
  }

  public Optional<FeeQuote> quote(Connection c, long student, long concept) throws SQLException {
    return Jdbc.query(
            c,
            "SELECT COALESCE(t.monto,p.monto_base),p.tasa_iva FROM conceptos_pago p LEFT JOIN "
                + "tarifas_estudiante t ON t.id_concepto=p.id_concepto AND t.id_estudiante=? "
                + "WHERE p.id_concepto=? AND p.esta_activo=TRUE",
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
        "INSERT INTO"
            + " obligaciones(id_estudiante,id_concepto,referencia,fecha_vencimiento,monto,tasa_iva)"
            + " VALUES(?,?,?,?,?,?)",
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
            "SELECT o.*,p.nombre_concepto FROM obligaciones o JOIN conceptos_pago p ON"
                + " p.id_concepto=o.id_concepto WHERE o.id_obligacion=? FOR UPDATE",
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
                "SELECT COALESCE(SUM(monto_pagado),0) FROM pagos WHERE id_obligacion=? AND"
                    + " estado_pago='Confirmado'",
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
            "SELECT p.*,f.id_factura FROM pagos p JOIN facturas f ON f.id_pago=p.id_pago WHERE"
                + " numero_recibo=?",
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
                      r.getDate("fecha_pago").toLocalDate(),
                      method,
                      r.getString("numero_recibo")));
            },
            receipt)
        .stream()
        .findFirst();
  }

  public Optional<Guardian> responsible(Connection c, long student, long guardian)
      throws SQLException {
    return Jdbc.query(
            c,
            "SELECT a.* FROM acudientes a JOIN estudiante_acudiente ea ON"
                + " ea.id_acudiente=a.id_acudiente WHERE ea.id_estudiante=? AND a.id_acudiente=?"
                + " AND ea.es_responsable_financiero=TRUE AND a.esta_activo=TRUE",
            r ->
                new Guardian(
                    r.getLong("id_acudiente"),
                    r.getString("numero_documento"),
                    r.getString("primer_nombre"),
                    r.getString("primer_apellido"),
                    r.getString("telefono_principal"),
                    r.getString("direccion")),
            student,
            guardian)
        .stream()
        .findFirst();
  }

  public long insertPayment(Connection c, Obligation o, PaymentRequest p) throws SQLException {
    return Jdbc.insert(
        c,
        "INSERT INTO"
            + " pagos(id_estudiante,id_acudiente,id_concepto,id_obligacion,monto_pagado,fecha_pago,metodo_pago,numero_recibo)"
            + " VALUES(?,?,?,?,?,?,?,?)",
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
      Guardian g,
      SchoolInformation s,
      BigDecimal subtotal,
      BigDecimal tax,
      LocalDate issueDate)
      throws SQLException {
    return Jdbc.insert(
        c,
        "INSERT INTO"
            + " facturas(numero_factura,id_pago,fecha_emision,cliente_nombre,cliente_documento,cliente_direccion,colegio_nombre,colegio_nit,colegio_direccion,colegio_telefono,colegio_correo,subtotal,iva,total,estado_pdf)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,'Pendiente')",
        UUID.randomUUID().toString(),
        payment,
        issueDate,
        g.firstName() + " " + g.lastName(),
        g.document(),
        Objects.toString(g.address(), ""),
        s.name(),
        s.nit(),
        s.address(),
        s.phone(),
        s.email(),
        subtotal,
        tax,
        request.amount());
  }

  public void identifyInvoice(Connection c, long id, String number, String cufe)
      throws SQLException {
    Jdbc.update(
        c, "UPDATE facturas SET numero_factura=?,cufe=? WHERE id_factura=?", number, cufe, id);
  }

  public void insertLine(Connection c, long invoice, InvoiceLine line) throws SQLException {
    Jdbc.update(
        c,
        "INSERT INTO detalles_factura(id_factura,descripcion,cantidad,valor_unitario,subtotal,iva)"
            + " VALUES(?,?,?,?,?,?)",
        invoice,
        line.description(),
        line.quantity(),
        line.unitPrice(),
        line.subtotal(),
        line.tax());
  }

  public Optional<Invoice> invoice(Connection c, long id) throws SQLException {
    List<InvoiceLine> lines =
        Jdbc.query(
            c,
            "SELECT * FROM detalles_factura WHERE id_factura=? ORDER BY id_detalle",
            r ->
                new InvoiceLine(
                    r.getString("descripcion"),
                    r.getInt("cantidad"),
                    r.getBigDecimal("valor_unitario"),
                    r.getBigDecimal("subtotal"),
                    r.getBigDecimal("iva")),
            id);
    return Jdbc.query(
            c,
            "SELECT * FROM facturas WHERE id_factura=?",
            r ->
                new Invoice(
                    r.getLong("id_factura"),
                    r.getLong("id_pago"),
                    r.getString("numero_factura"),
                    r.getDate("fecha_emision").toLocalDate(),
                    r.getString("cliente_nombre"),
                    r.getString("cliente_documento"),
                    r.getString("cliente_direccion"),
                    r.getString("colegio_nombre"),
                    r.getString("colegio_nit"),
                    r.getString("colegio_direccion"),
                    r.getString("colegio_telefono"),
                    r.getString("colegio_correo"),
                    r.getBigDecimal("subtotal"),
                    r.getBigDecimal("iva"),
                    r.getBigDecimal("total"),
                    r.getString("cufe"),
                    r.getString("estado_pdf"),
                    r.getString("ruta_archivo"),
                    lines,
                    r.getString("estado_factura"),
                    r.getString("motivo_anulacion")),
            id)
        .stream()
        .findFirst();
  }

  public void pdfStatus(Connection c, long id, String status, String path) throws SQLException {
    Jdbc.update(
        c,
        "UPDATE facturas SET"
            + " estado_pdf=?,ruta_archivo=COALESCE(?,ruta_archivo),nombre_archivo_pdf=COALESCE(?,nombre_archivo_pdf)"
            + " WHERE id_factura=? AND (estado_pdf<>'Generada' OR ?='Generada')",
        status,
        path,
        path == null ? null : java.nio.file.Path.of(path).getFileName().toString(),
        id,
        status);
  }

  public List<Balance> balances(Connection c, LocalDate cutoff) throws SQLException {
    return Jdbc.query(
        c,
        "SELECT"
            + " o.id_obligacion,o.id_estudiante,p.nombre_concepto,o.fecha_vencimiento,o.monto-COALESCE(SUM(pg.monto_pagado),0)"
            + " saldo FROM obligaciones o JOIN conceptos_pago p ON p.id_concepto=o.id_concepto LEFT"
            + " JOIN pagos pg ON pg.id_obligacion=o.id_obligacion AND pg.estado_pago='Confirmado'"
            + " WHERE o.estado='Activa' AND o.fecha_vencimiento<? GROUP BY"
            + " o.id_obligacion,o.id_estudiante,p.nombre_concepto,o.fecha_vencimiento,o.monto"
            + " HAVING saldo>0 ORDER BY o.fecha_vencimiento",
        r ->
            new Balance(
                r.getLong(1),
                r.getLong(2),
                r.getString(3),
                r.getDate(4).toLocalDate(),
                r.getBigDecimal(5)),
        cutoff);
  }

  public List<String> history(Connection c, long id, boolean guardian) throws SQLException {
    String filter =
        guardian
            ? "EXISTS(SELECT 1 FROM acudientes a WHERE a.id_acudiente=? AND"
                + " (p.id_acudiente=a.id_acudiente OR EXISTS(SELECT 1 FROM estudiante_acudiente"
                + " ea WHERE ea.id_estudiante=p.id_estudiante AND"
                + " ea.id_acudiente=a.id_acudiente)))"
            : "p.id_estudiante=?";
    return Jdbc.query(
        c,
        "SELECT p.id_pago,p.id_estudiante,p.fecha_pago,p.monto_pagado,p.numero_recibo,p.estado_pago"
            + " FROM pagos p WHERE "
            + filter
            + " ORDER BY p.fecha_pago,p.id_pago",
        r ->
            r.getLong(1)
                + " | Estudiante "
                + r.getLong(2)
                + " | "
                + r.getDate(3)
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
    return Jdbc.query(
            c,
            "SELECT * FROM conceptos_pago WHERE id_concepto=?" + (lock ? " FOR UPDATE" : ""),
            this::concept,
            id)
        .stream()
        .findFirst();
  }

  public int updateConcept(Connection c, PaymentConcept v) throws SQLException {
    return Jdbc.update(
        c,
        "UPDATE conceptos_pago SET"
            + " codigo_concepto=?,nombre_concepto=?,monto_base=?,tasa_iva=?,esta_activo=? WHERE"
            + " id_concepto=?",
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
        "SELECT id_estudiante,id_concepto,monto FROM tarifas_estudiante WHERE id_estudiante=? ORDER"
            + " BY id_concepto",
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
        r.getDate("fecha_vencimiento").toLocalDate(),
        r.getBigDecimal("monto"),
        r.getBigDecimal("tasa_iva"),
        r.getString("estado"));
  }

  public List<ObligationEntry> obligations(Connection c) throws SQLException {
    return Jdbc.query(c, "SELECT * FROM obligaciones ORDER BY id_obligacion", this::obligation);
  }

  public Optional<ObligationEntry> obligation(Connection c, long id, boolean lock)
      throws SQLException {
    return Jdbc.query(
            c,
            "SELECT * FROM obligaciones WHERE id_obligacion=?" + (lock ? " FOR UPDATE" : ""),
            this::obligation,
            id)
        .stream()
        .findFirst();
  }

  public int updateObligation(Connection c, ObligationEntry v) throws SQLException {
    return Jdbc.update(
        c,
        "UPDATE obligaciones SET referencia=?,fecha_vencimiento=?,monto=?,estado=? WHERE"
            + " id_obligacion=?",
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
            r.getDate("fecha_pago").toLocalDate(),
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
    return Jdbc.query(
            c,
            "SELECT * FROM pagos WHERE id_pago=?" + (lock ? " FOR UPDATE" : ""),
            this::payment,
            id)
        .stream()
        .findFirst();
  }

  public Optional<Long> invoiceId(Connection c, long payment) throws SQLException {
    return Jdbc.query(
            c, "SELECT id_factura FROM facturas WHERE id_pago=?", r -> r.getLong(1), payment)
        .stream()
        .findFirst();
  }

  public int voidPayment(Connection c, long id, String reason) throws SQLException {
    int changed =
        Jdbc.update(
            c,
            "UPDATE pagos SET estado_pago='Anulado',motivo_anulacion=? WHERE id_pago=? AND"
                + " estado_pago='Confirmado'",
            reason,
            id);
    if (changed > 0)
      Jdbc.update(
          c,
          "UPDATE facturas SET estado_factura='Anulada',motivo_anulacion=?,estado_pdf='Fallida'"
              + " WHERE id_pago=?",
          reason,
          id);
    return changed;
  }

  public void markReplacement(Connection c, long replacement, long original) throws SQLException {
    Jdbc.update(c, "UPDATE pagos SET id_pago_original=? WHERE id_pago=?", original, replacement);
  }

  public List<Invoice> invoices(Connection c) throws SQLException {
    var ids =
        Jdbc.query(c, "SELECT id_factura FROM facturas ORDER BY id_factura", r -> r.getLong(1));
    List<Invoice> result = new ArrayList<>();
    for (long id : ids) result.add(invoice(c, id).orElseThrow());
    return result;
  }

  public boolean activeStudent(Connection c, long student) throws SQLException {
    return !Jdbc.query(
            c,
            "SELECT id_estudiante FROM estudiantes WHERE id_estudiante=? AND estado='Activo' FOR"
                + " UPDATE",
            r -> r.getLong(1),
            student)
        .isEmpty();
  }

  public boolean markPdfGenerated(Connection c, long id, String path, String expectedState)
      throws SQLException {
    if (Jdbc.query(
            c,
            "SELECT id_factura FROM facturas WHERE id_factura=? AND estado_factura=? FOR UPDATE",
            r -> r.getLong(1),
            id,
            expectedState)
        .isEmpty()) return false;
    pdfStatus(c, id, "Generada", path);
    return true;
  }
}
