package de.moeri

import io.ktor.util.KtorExperimentalAPI
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter


@KtorExperimentalAPI
object ExcelWriter {
    private val columns = arrayOf("Datum", "Art", "Beschreibung", "Name")


    fun gen() {
        val workbook: Workbook = XSSFWorkbook() // new HSSFWorkbook() for generating `.xls` file
        val createHelper: CreationHelper = workbook.creationHelper
        val sheet: Sheet = workbook.createSheet("Export")
        val headerFont: Font = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 14.toShort()
            color = IndexedColors.RED.getIndex()
        }
        val headerCellStyle: CellStyle = workbook.createCellStyle().apply {
            setFont(headerFont)
        }
        val headerRow: Row = sheet.createRow(0)



        for (i in columns.indices) {
            headerRow.createCell(i).apply {
                setCellValue(columns[i])
                cellStyle = headerCellStyle
            }
        }

        val formatter =  SimpleDateFormat("dd.MM.yyyy")
        var rowNum = 1
        for (entry in Database.list("chosen", "date")) {
            val row: Row = sheet.createRow(rowNum++)
            row.createCell(0)
                .setCellValue(formatter.format(entry.date))
            row.createCell(1)
                .setCellValue(if (entry.type == Type.HISTORIC) "Historisch" else "Persönlich")
            row.createCell(2)
                .setCellValue(entry.description)
            row.createCell(3)
                .setCellValue(entry.name)
        }

        // Resize all columns to fit the content size
        for (i in columns.indices) {
            sheet.autoSizeColumn(i)
        }

        // Write the output to a file
        val fileOut = FileOutputStream("kds-chosen-events-export.xlsx")
        workbook.write(fileOut)
        fileOut.close()

        // Closing the workbook
        workbook.close()
    }
}
