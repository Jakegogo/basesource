package com.concur.basesource.reader;

import com.concur.basesource.convertor.utils.SheetInfo;
import com.concur.basesource.convertor.utils.SheetUtils;
import com.concur.unity.reflect.ReflectionUtility;
import com.concur.unity.utils.ConvertUtils;
import com.concur.unity.utils.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;


/**
 * Excel格式的静态资源读取器
 * @author frank
 */
@Component
public class ExcelReader implements ResourceReader {
	
	private final static Logger logger = LoggerFactory.getLogger(ExcelReader.class);
	
	/** 服务端控制标识同时也是数据开始标识 */
	public final static String ROW_SERVER = "SERVER";
	/** 客户端控制标识同时也是数据开始标识 */
	public final static String ROW_CLIENT = "CLIENT";
	/** 结束标识 */
	public final static String ROW_END = "END";
	
	@Override
	public String getFormat() {
		return ReaderType.EXCEL.getType();
	}

	/**
	 * 属性信息
	 * @author frank
	 */
	private static class FieldInfo {
		/** 第几列 */
		public final int index;
		/** 资源类属性 */
		public final Field field;
		/** 构造方法 */
		public FieldInfo(int index, Field field) {
			ReflectionUtility.makeAccessible(field);
			this.index = index;
			this.field = field;
		}
	}

	@Override
	public <E> Iterator<E> read(InputStream input, Class<E> clz) {
		// 基本信息获取
		Workbook wb = SheetUtils.getWorkbook(input, clz.getName());
		Map<String, SheetInfo> sheetInfos = SheetUtils.listSheets(wb, null);

		// 创建返回数据集
		ArrayList<E> result = new ArrayList<E>();
		SheetInfo sheetInfo = sheetInfos.get(clz.getSimpleName());
		if (sheetInfo == null) {
			return (Iterator<E>) Collections.emptyList().iterator();
		}

		for (Sheet sheet : sheetInfo.sheets) {
			Collection<FieldInfo> infos = getCellInfos(sheet, clz);
			boolean start = false;
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
						if (content.equals(ROW_SERVER)) {
							start = true;
						}
						continue;
					}
					// 生成返回对象
					E instance = newInstance(clz);
					for (FieldInfo info : infos) {
						Cell cell = row.getCell(info.index);
						if (cell == null) {
							continue;
						}
						String content = getCellContent(cell);
						if (StringUtils.isEmpty(content)) {
							continue;
						}
						inject(instance, info.field, content);
					}
					result.add(instance);

					// 结束处理
					Cell cell = row.getCell(0);
					if (cell == null) {
						continue;
					}
					String content = getCellContent(cell);
					if (content == null) {
						continue;
					}
					if (content.equals(ROW_END)) {
						break;
					}
				} catch (RuntimeException e) {
					logger.error("读取资源文件{}的第{}行出错", clz.getSimpleName(), i);
					throw e;
				}
			}
		}
		return result.iterator();
	}

	/**
	 * 获取字符串形式的单元格内容
	 * @param cell
	 * @return
	 */
	private String getCellContent(Cell cell) {
		if (cell.getCellType() != Cell.CELL_TYPE_STRING) {
			cell.setCellType(Cell.CELL_TYPE_STRING);
		}
		return cell.getStringCellValue();
	}
	
	/**
	 * 给实例注入属性
	 * @param instance
	 * @param field
	 * @param content
	 */
	private void inject(Object instance, Field field, String content) {
		// Class<?> clz = field.getType();
		try {
			TypeDescriptor targetType = new TypeDescriptor(field);
			Object value = ConvertUtils.convert(content, targetType.getType());
			field.set(instance, value);
		} catch (ConverterNotFoundException e) {
			FormattingTuple message = MessageFormatter.format("静态资源[{}]属性[{}]的转换器不存在", instance.getClass()
					.getSimpleName(), field.getName());
			logger.error(message.getMessage(), e);
			throw new IllegalStateException(message.getMessage(), e);
		} catch (Exception e) {
			FormattingTuple message = MessageFormatter.format("属性[{}]注入失败", field);
			logger.error(message.getMessage());
			throw new IllegalStateException(message.getMessage(), e);
		}
	}
	
	/**
	 * 实例化资源
	 * @param <E>
	 * @param clz
	 * @return
	 */
	private <E> E newInstance(Class<E> clz) {
		try {
			return clz.newInstance();
		} catch (Exception e) {
			FormattingTuple message = MessageFormatter.format("资源[{}]无法实例化", clz);
			logger.error(message.getMessage());
			throw new RuntimeException(message.getMessage());
		}
	}
	
	/**
	 * 获取表格信息
	 * @param sheet
	 * @param clz
	 * @return
	 */
	private Collection<FieldInfo> getCellInfos(Sheet sheet, Class<?> clz) {
		// 获取属性控制行
		Row fieldRow = SheetUtils.getFieldRow(sheet);
		if (fieldRow == null) {
			FormattingTuple message = MessageFormatter.format("无法获取资源[{}]的EXCEL文件的属性控制列", clz);
			logger.error(message.getMessage());
			throw new IllegalStateException(message.getMessage());
		}
		
		// 获取属性信息集合
		List<FieldInfo> result = new ArrayList<FieldInfo>();
		for (int i = 1; i < fieldRow.getLastCellNum(); i++) {
			Cell cell = fieldRow.getCell(i);
			if (cell == null) {
				continue;
			}
			
			String name = getCellContent(cell);
			if (StringUtils.isBlank(name)) {
				continue;
			}
			
			try {
				Field field = clz.getDeclaredField(name);
				FieldInfo info = new FieldInfo(i, field);
				result.add(info);
			} catch (Exception e) {
				FormattingTuple message = MessageFormatter.format("资源类[{}]的声明属性[{}]不存在", clz, name);
				logger.warn(message.getMessage());
			}
		}
		return result;
	}
	
}
