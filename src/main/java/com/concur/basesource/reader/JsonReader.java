package com.concur.basesource.reader;

import com.concur.basesource.exceptions.DecodeException;
import com.concur.unity.utils.JsonUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * JSON 资源读取器
 * @author frank
 */
@Component
public class JsonReader implements ResourceReader {
	
	@Override
	public <E> Iterator<E> read(InputStream input, Class<E> clz) {
		try {
			StringWriter writer = new StringWriter();
			IOUtils.copy(input, writer, "utf-8");
			String theString = writer.toString();
			List<E> list = JsonUtils.jsonString2Object(theString, ArrayList.class);
			return list.iterator();
		} catch (Exception e) {
			throw new DecodeException(e);
		}
	}

	@Override
	public String getFormat() {
		return ReaderType.JSON.getType();
	}

}
