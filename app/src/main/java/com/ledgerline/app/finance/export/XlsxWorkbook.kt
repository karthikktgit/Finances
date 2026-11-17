package com.ledgerline.app.finance.export

import com.ledgerline.app.finance.money.MoneyFormat
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A typed spreadsheet cell. Plain [String] values are written as text.
 * Money, counts, and dates are written as real numbers so spreadsheets can sum and sort them.
 */
sealed interface XlsxCell {
    /** An amount in minor units. It is converted to major units exactly once, when the cell is written. */
    data class Money(val minor: Long, val currency: String) : XlsxCell

    data class Count(val value: Long) : XlsxCell

    data class Date(val value: LocalDate) : XlsxCell
}

/** Cells may be [String], [XlsxCell], or anything else, which is written as its text. */
data class XlsxSheet(
    val name: String,
    val rows: List<List<Any>>,
)

object XlsxWorkbook {
    fun write(sheets: List<XlsxSheet>): ByteArray {
        val safe = sheets.ifEmpty { listOf(XlsxSheet("Report", listOf(listOf("No rows")))) }
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            zip.put(" [Content_Types].xml".trim(), contentTypes(safe.size))
            zip.put("_rels/.rels", RELS)
            zip.put("xl/workbook.xml", workbook(safe))
            zip.put("xl/_rels/workbook.xml.rels", workbookRels(safe.size))
            zip.put("xl/styles.xml", STYLES)
            safe.forEachIndexed { index, sheet ->
                zip.put("xl/worksheets/sheet${index + 1}.xml", worksheet(sheet.rows))
            }
        }
        return buffer.toByteArray()
    }

    private fun ZipOutputStream.put(name: String, xml: String) {
        putNextEntry(ZipEntry(name))
        write(xml.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun contentTypes(count: Int): String {
        val sheets = (1..count).joinToString("") {
            """<Override PartName="/xl/worksheets/sheet$it.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
$sheets
</Types>"""
    }

    private fun workbook(sheets: List<XlsxSheet>): String {
        val entries = sheets.mapIndexed { index, sheet ->
            """<sheet name="${escape(sheet.name.take(31))}" sheetId="${index + 1}" r:id="rId${index + 1}"/>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>$entries</sheets>
</workbook>"""
    }

    private fun workbookRels(count: Int): String {
        val rels = (1..count).joinToString("") {
            """<Relationship Id="rId$it" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$it.xml"/>"""
        }
        val styles = """<Relationship Id="rId${count + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>"""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels$styles</Relationships>"""
    }

    private fun worksheet(rows: List<List<Any>>): String {
        val body = rows.mapIndexed { rowIndex, row ->
            val cells = row.mapIndexed { column, value -> cell(columnName(column) + (rowIndex + 1), value) }.joinToString("")
            """<row r="${rowIndex + 1}">$cells</row>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$body</sheetData></worksheet>"""
    }

    private fun cell(ref: String, value: Any): String = when (value) {
        is XlsxCell.Money -> {
            val digits = MoneyFormat.fractionDigits(value.currency)
            """<c r="$ref" s="${moneyStyle(digits)}"><v>${MoneyFormat.formatPlain(value.minor, value.currency)}</v></c>"""
        }
        is XlsxCell.Count -> """<c r="$ref"><v>${value.value}</v></c>"""
        is XlsxCell.Date -> """<c r="$ref" s="$DATE_STYLE"><v>${serial(value.value)}</v></c>"""
        else -> """<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${escape(value.toString())}</t></is></c>"""
    }

    /** Excel day numbers count from 1899-12-30. This is calendar arithmetic, so no time zone can move the date. */
    fun serial(date: LocalDate): Long = date.toEpochDay() + EPOCH_OFFSET

    private fun moneyStyle(digits: Int): Int = when (digits) {
        0 -> 2
        1 -> 3
        2 -> 4
        3 -> 5
        else -> 0
    }

    private fun columnName(index: Int): String {
        var n = index
        val chars = StringBuilder()
        do {
            chars.append(('A'.code + (n % 26)).toChar())
            n = n / 26 - 1
        } while (n >= 0)
        return chars.reverse().toString()
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private const val DATE_STYLE = 1
    private const val EPOCH_OFFSET = 25_569L

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="3"><numFmt numFmtId="164" formatCode="yyyy\-mm\-dd"/><numFmt numFmtId="165" formatCode="#,##0.0"/><numFmt numFmtId="166" formatCode="#,##0.000"/></numFmts>
<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="6">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="3" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="166" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""

    private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
}
