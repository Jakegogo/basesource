package com.concur.basesource.convertor.utils;

import com.concur.basesource.convertor.task.ProgressMonitorInputStream;
import com.concur.basesource.reader.ExcelReader;
import com.concur.unity.utils.StringUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @description: Excel工具类
 * @author: Jake
 * @create: 2018-05-13 23:13
 **/
public class SheetUtils {

    private static final Logger logger = LoggerFactory.getLogger(SheetUtils.class);
    /**
     * 获取资源类型对应的工作簿
     * @param wb Excel Workbook
     * @return 基础班名称 - List<Sheet>
     */
    public static Map<String, SheetInfo> listSheets(Workbook wb, File file) {
        try {
            Map<String, SheetInfo> result = new HashMap<String, SheetInfo>();
            // 处理多Sheet数据合并
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                if (sheet.getLastRowNum() <= 0) {
                    continue;
                }
                Row row = sheet.getRow(0);
                if (row == null) {
                    continue;
                }
                if (row.getLastCellNum() <= 0) {
                    continue;
                }
                Cell cell = row.getCell(0);
                if (cell == null) {
                    continue;
                }
                // 获取属性控制行
                Row fieldRow = getFieldRow(sheet);
                if (fieldRow == null) {
                    continue;
                }
                if (cell.getCellType() != Cell.CELL_TYPE_STRING) {
                    cell.setCellType(Cell.CELL_TYPE_STRING);
                }
                String text = cell.getStringCellValue();
                if (StringUtils.isBlank(text)) {
                    continue;
                }

                SheetInfo sheetInfo = result.get(text);
                if (sheetInfo == null) {
                    sheetInfo = new SheetInfo();
                    sheetInfo.file = file;
                    sheetInfo.sheets = new ArrayList<Sheet>();
                    result.put(text, sheetInfo);
                }
                sheetInfo.sheets.add(sheet);
            }

            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("无法获取资源类[" + (file != null ? file.getPath() : "") + "]对应的Excel数据表", e);
        }
    }

    /**
     * 获取列信息
     * @param sheet
     * @return
     */
    public static List<ColumnInfo> getColumnInfo(Sheet sheet) {
        // 获取属性控制行
        Row fieldRow = getFieldRow(sheet);
        if (fieldRow == null) {
            FormattingTuple message = MessageFormatter.format("无法获取资源[ {} ]的EXCEL文件的属性控制列", sheet.getSheetName());
            throw new IllegalStateException(message.getMessage());
        }

        // 获取属性信息集合
        List<ColumnInfo> result = new ArrayList<ColumnInfo>();
        for (int i = 1; i < fieldRow.getLastCellNum(); i++) {
            Cell cell = fieldRow.getCell(i);
            if (cell == null) {
                continue;
            }

            String name = getCellContent(cell);
            if (StringUtils.isBlank(name)) {
                continue;
            }

            ColumnInfo info = new ColumnInfo();
            info.name = name;
            info.column = i;
            result.add(info);
        }
        return result;
    }

    /**
     * 通过输入流获取{@link Workbook}
     * @param file
     * @return
     */
    public static Workbook getWorkbook(final File file) {
        try {
            return WorkbookFactory.create(new ProgressMonitorInputStream(new FileInputStream(file), 0.1f) {
                @Override
                public void updateProgress(double progresss) {
                    logger.info("资源文件:{}正在加载:{}%", file.getName(), (int) (progresss * 100));
                }
            });
        } catch (InvalidFormatException e) {
            throw new RuntimeException("静态资源[" + file.getPath() + "]异常,无效的文件格式", e);
        } catch (IOException e) {
            throw new RuntimeException("静态资源[" + file.getPath() + "]异常,无法读取文件", e);
        }
    }

    /**
     * 通过输入流获取{@link Workbook}
     * @param input
     * @return
     */
    public static Workbook getWorkbook(final InputStream input, final String name) {
        try {
            return WorkbookFactory.create(new ProgressMonitorInputStream(input, 0.1f) {
                @Override
                public void updateProgress(double progresss) {
                    logger.info("资源文件:{}正在加载:{}%", name, (int) (progresss * 100));
                }
            });
        } catch (InvalidFormatException e) {
            throw new RuntimeException("静态资源[" + name + "]异常,无效的文件格式", e);
        } catch (IOException e) {
            throw new RuntimeException("静态资源[" + name + "]异常,无法读取文件", e);
        }
    }

    /**
     * 获取属性控制行
     * @param sheet
     * @return
     */
    public static Row getFieldRow(Sheet sheet) {
        for (Row row : sheet) {
            Cell cell = row.getCell(0);
            if (cell == null) {
                continue;
            }
            String content = getCellContent(cell);
            if (content != null && content.equals(ExcelReader.ROW_SERVER)) {
                return row;
            }
        }
        return null;
    }

    /**
     * 获取字符串形式的单元格内容
     * @param cell
     * @return
     */
    public static String getCellContent(Cell cell) {
        if (cell.getCellType() != Cell.CELL_TYPE_STRING) {
            cell.setCellType(Cell.CELL_TYPE_STRING);
        }
        return cell.getStringCellValue();
    }

    /**
     * 读取表格内容
     * @param sheetInfo SheetInfo
     * @param maxProgress maxProgress 最大进度展示 < 1
     * @param progressAwares ProgressAware 进度通知回调
     * @return
     */
    public static List<Map<String, String>> readSheetData(SheetInfo sheetInfo, double maxProgress, ProgressAware... progressAwares) {
        List<Map<String, String>> beanList = new ArrayList<Map<String, String>>();
        Collection<ColumnInfo> infos = getColumnInfo(sheetInfo.sheets.get(0));

        int size = sheetInfo.sheets.size();
        int curSheetIndex = 0;

        for (Sheet sheet : sheetInfo.sheets) {
            boolean start = false;

            int rowSize = sheet.getLastRowNum();
            int curRowIndex = 0;

            int i = 0;
            for (Row row : sheet) {

                try {
                    i++;
                    // 判断数据行开始没有
                    if (!start) {
                        Cell cell = row.getCell(0);
                        if (cell == null) {
                            continue;
                        }
                        String content = getCellContent(cell);
                        if (content == null) {
                            continue;
                        }
                        if (content.equals(ExcelReader.ROW_SERVER)) {
                            start = true;
                        }
                        continue;
                    }

                    // 生成返回对象
                    Map<String, String> object = new HashMap<String, String>();
                    for (ColumnInfo info : infos) {
                        Cell cell = row.getCell(info.column);
                        if (cell == null) {
                            continue;
                        }
                        String content = getCellContent(cell);
                        if (StringUtils.isEmpty(content)) {
                            continue;
                        }
                        object.put(info.name, content);
                    }
                    beanList.add(object);

                    // 结束处理
                    Cell cell = row.getCell(0);
                    if (cell == null) {
                        continue;
                    }
                    String content = getCellContent(cell);
                    if (content == null) {
                        continue;
                    }
                    if (content.equals(ExcelReader.ROW_END)) {
                        break;
                    }

                    curRowIndex++;
                    if (progressAwares != null && progressAwares.length > 0) {
                        for (ProgressAware progressAware : progressAwares) {
                            progressAware.onProgress(((double) curRowIndex) / rowSize * (((double) curSheetIndex) / size) * maxProgress);
                        }
                    }
                } catch (RuntimeException e) {
                    logger.error("读取资源文件{}的第{}行出错", sheetInfo.file.getName(), i);
                    throw e;
                }
            }

            curSheetIndex ++;
            if (progressAwares != null && progressAwares.length > 0) {
                for (ProgressAware progressAware : progressAwares) {
                    progressAware.onProgress(((double)curSheetIndex) / size * maxProgress);
                }
            }

        }

        return beanList;
    }

    /**
     * 进度通知
     */
    public interface ProgressAware {
        void onProgress(double percent);
    }

}
