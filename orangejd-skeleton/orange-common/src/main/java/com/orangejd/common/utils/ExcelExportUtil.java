package com.orangejd.common.utils;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public final class ExcelExportUtil {
    private ExcelExportUtil() {
    }

    public static void writeSimpleReport(OutputStream outputStream, String sheetName, List<String> headers, List<List<String>> rows) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(sheetName);

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            headerRow.createCell(i).setCellValue(headers.get(i));
        }

        for (int i = 0; i < rows.size(); i++) {
            Row row = sheet.createRow(i + 1);
            List<String> values = rows.get(i);
            for (int j = 0; j < values.size(); j++) {
                row.createCell(j).setCellValue(values.get(j));
            }
        }

        workbook.write(outputStream);
        workbook.close();
    }
}

