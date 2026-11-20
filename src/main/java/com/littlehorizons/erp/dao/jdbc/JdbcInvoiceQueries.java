package com.littlehorizons.erp.dao.jdbc;

import com.littlehorizons.erp.model.Invoice;
import com.littlehorizons.erp.model.InvoiceCustomer;
import com.littlehorizons.erp.model.InvoiceLine;
import com.littlehorizons.erp.model.PaymentRequest;
import com.littlehorizons.erp.util.SchoolInformation;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Persists invoice snapshots and file state independently of file rendering. */
final class JdbcInvoiceQueries {
  long insertInvoice(
      Connection c,
      long payment,
      PaymentRequest request,
      InvoiceCustomer customer,
      SchoolInformation s,
      BigDecimal subtotal,
      BigDecimal tax,
      LocalDateTime issuedAt)
      throws SQLException {
    // The auto-generated identifier is needed before the final invoice number and CUFE can be set.
    return Jdbc.insert(
        c,
        """
INSERT INTO
facturas(numero_factura,id_pago,fecha_emision,hora_emision,cliente_nombre,cliente_documento,cliente_direccion,colegio_nombre,colegio_nit,colegio_direccion,colegio_telefono,colegio_correo,subtotal,iva,total,estado_archivo)
VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'Pendiente')
""",
        UUID.randomUUID().toString(),
        payment,
        issuedAt.toLocalDate(),
        issuedAt.toLocalTime(),
        customer.fullName(),
        customer.document(),
        Objects.toString(customer.address(), ""),
        s.name(),
        s.nit(),
        s.address(),
        s.phone(),
        s.email(),
        subtotal,
        tax,
        request.amount());
  }

  void identifyInvoice(Connection c, long id, String number, String cufe)
      throws SQLException {
    Jdbc.update(
        c, "UPDATE facturas SET numero_factura=?,cufe=? WHERE id_factura=?", number, cufe, id);
  }

  void insertLine(Connection c, long invoice, InvoiceLine line) throws SQLException {
    Jdbc.update(
        c,
        """
        INSERT INTO detalles_factura(id_factura,descripcion,cantidad,valor_unitario,subtotal,iva)
        VALUES(?,?,?,?,?,?)
        """,
        invoice,
        line.description(),
        line.quantity(),
        line.unitPrice(),
        line.subtotal(),
        line.tax());
  }

  Optional<Invoice> invoice(Connection c, long id) throws SQLException {
    // Read the original customer and school details from the stored invoice snapshot.
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
                    r.getObject("fecha_emision", LocalDate.class),
                    r.getObject("hora_emision", LocalTime.class),
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
                    r.getString("estado_archivo"),
                    r.getString("ruta_archivo"),
                    lines,
                    r.getString("estado_factura"),
                    r.getString("motivo_anulacion")),
            id)
        .stream()
        .findFirst();
  }

  int fileStatus(Connection c, long id, String status, String path) throws SQLException {
    return Jdbc.update(
        c,
        """
        UPDATE facturas SET estado_archivo=?,ruta_archivo=COALESCE(?,ruta_archivo),
            nombre_archivo=COALESCE(?,nombre_archivo)
        WHERE id_factura=?
        """,
        status,
        path,
        path == null ? null : java.nio.file.Path.of(path).getFileName().toString(),
        id);
  }

  Optional<Long> invoiceId(Connection c, long payment) throws SQLException {
    return Jdbc.query(
            c, "SELECT id_factura FROM facturas WHERE id_pago=?", r -> r.getLong(1), payment)
        .stream()
        .findFirst();
  }

  void cancelForPayment(Connection c, long payment, String reason) throws SQLException {
    // The previous file no longer represents the cancelled invoice until it is regenerated.
    Jdbc.update(
        c,
        """
        UPDATE facturas SET estado_factura='Anulada',motivo_anulacion=?,estado_archivo='Fallida'
        WHERE id_pago=?
        """,
        reason,
        payment);
  }

  List<Invoice> invoices(Connection c) throws SQLException {
    var ids =
        Jdbc.query(c, "SELECT id_factura FROM facturas ORDER BY id_factura", r -> r.getLong(1));
    List<Invoice> result = new ArrayList<>();
    for (long id : ids) result.add(invoice(c, id).orElseThrow());
    return result;
  }

  boolean markFileGenerated(Connection c, long id, String path, String expectedState)
      throws SQLException {
    // Refuse a stale render if the invoice was cancelled while the document was being written.
    if (Jdbc.query(
            c,
            "SELECT id_factura FROM facturas WHERE id_factura=? AND estado_factura=? FOR UPDATE",
            r -> r.getLong(1),
            id,
            expectedState)
        .isEmpty()) return false;
    return fileStatus(c, id, "Generada", path) > 0;
  }
}
