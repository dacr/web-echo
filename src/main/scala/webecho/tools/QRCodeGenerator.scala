package webecho.tools

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream
import java.util.Base64

object QRCodeGenerator {
  def generateQRCode(text: String, width: Int = 250, height: Int = 250): ByteArrayOutputStream = {
    val writer = new QRCodeWriter()
    val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height)
    val stream = new ByteArrayOutputStream()
    MatrixToImageWriter.writeToStream(bitMatrix, "PNG", stream)
    stream
  }

  def generateQRCodePng(text: String, width: Int = 250, height: Int = 250): Array[Byte] = {
    generateQRCode(text, width, height).toByteArray
  }

  def generateQRCodeBase64(text: String, width: Int = 250, height: Int = 250): String = {
    Base64.getEncoder.encodeToString(generateQRCodePng(text, width, height))
  }
}