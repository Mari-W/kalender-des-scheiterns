package de.moeri

import io.ktor.util.KtorExperimentalAPI
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileOutputStream
import java.io.IOException
import java.util.*


@KtorExperimentalAPI
object ExcelWriter {
    private val columns = arrayOf("Datum", "Art", "Beschreibung", "Name")


    // Initializing employees data to insert into the excel file
    fun gen() {
        // Create a Workbook
        val workbook: Workbook = XSSFWorkbook() // new HSSFWorkbook() for generating `.xls` file

        /* CreationHelper helps us create instances of various things like DataFormat,
           Hyperlink, RichTextString etc, in a format (HSSF, XSSF) independent way */
        val createHelper: CreationHelper = workbook.creationHelper

        // Create a Sheet
        val sheet: Sheet = workbook.createSheet("Export")

        // Create a Font for styling header cells
        val headerFont: Font = workbook.createFont()
        headerFont.bold = true
        headerFont.fontHeightInPoints = 14.toShort()
        headerFont.color = IndexedColors.RED.getIndex()

        // Create a CellStyle with the font
        val headerCellStyle: CellStyle = workbook.createCellStyle()
        headerCellStyle.setFont(headerFont)

        // Create a Row
        val headerRow: Row = sheet.createRow(0)

        // Create cells
        for (i in columns.indices) {
            val cell: Cell = headerRow.createCell(i)
            cell.setCellValue(columns[i])
            cell.cellStyle = headerCellStyle
        }

        // Create Cell Style for formatting Date
        val dateCellStyle: CellStyle = workbook.createCellStyle()
        dateCellStyle.dataFormat = createHelper.createDataFormat().getFormat("dd-MM-yyyy")

        // Create Other rows and cells with employees data
        var rowNum = 1
        for (entry in Database.list("chosen", "date")) {
            val row: Row = sheet.createRow(rowNum++)
            val dateOfBirthCell: Cell = row.createCell(0)
            dateOfBirthCell.setCellValue(entry.date)
            dateOfBirthCell.cellStyle = dateCellStyle
            row.createCell(1)
                .setCellValue(if(entry.type == Type.HISTORIC) "Histroisch" else "Persönlich")
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