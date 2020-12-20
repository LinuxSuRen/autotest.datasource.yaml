/*
 *
 *  * Copyright 2002-2007 the original author or authors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.surenpi.autotest.datasource.yaml;

import com.surenpi.autotest.datasource.*;
import com.surenpi.autotest.webui.Page;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * YAML格式的数据源
 * @author suren
 * @date 2017年5月10日 下午2:22:30
 */
@Component(DataSourceConstants.DS_TYPE_YAML)
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class YamlDataSource implements DataSource<Page>, DynamicDataSource
{
	@Autowired
	private List<DynamicData> dynamicDataList;
	
	private URL url;
	private Map<String, Object> globalMap = new HashMap<String, Object>();

	@Override
	public boolean loadData(DataResource resource, Page page)
	{
		return loadData(resource, 0, page);
	}

	@Override
	public boolean loadData(DataResource resource, int row, Page page)
	{
		try
		{
			url = resource.getUrl();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		
		if(url == null)
		{
			return false;
		}
		
		try(InputStream input = url.openStream())
		{
			parse(input, page);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		
		return true;
	}

	/**
	 * @param input
	 */
	private void parse(InputStream input, Page targetPage)
	{
		Yaml yaml = new Yaml();
		
		Map map = (Map) yaml.load(input);
		
		String pageName = targetPage.getClass().getName();
		if(map.containsKey("package") && map.get("package") != null)
		{
			String pkg = map.get("package").toString();
			
			pageName = pageName.replace(pkg + ".", "");
		}
		
		if(!map.containsKey(pageName))
		{
			return;
		}
		
		Object fieldsObj = map.get(pageName);
		if(fieldsObj instanceof Map)
		{
			Map fieldMap = (Map) fieldsObj;
			pageParse(fieldMap, targetPage);
		}
	}

	/**
	 * @param fieldMap
	 * @param targetPage
	 */
	private void pageParse(Map fieldMap, Page targetPage)
	{
		Class<?> targetPageCls = targetPage.getClass();
		List<Field> fieldList = new ArrayList<Field>();
		
		for(; targetPageCls != Page.class; targetPageCls = targetPageCls.getSuperclass())
		{
			Field[] decFields = targetPageCls.getDeclaredFields();
			
			fieldList.addAll(Arrays.asList(decFields));
		}
		
		for(Field field : fieldList)
		{
			String name = field.getName();
			Object data = fieldMap.get(name);
			
			try
			{
				setValue(field, targetPage, data);
			}
			catch (IllegalArgumentException | IllegalAccessException e)
			{
				e.printStackTrace();
			}
			catch (NoSuchMethodException e)
			{
				e.printStackTrace();
			}
			catch (SecurityException e)
			{
				e.printStackTrace();
			}
			catch (InvocationTargetException e)
			{
				e.printStackTrace();
			}
		}
	}

	/**
	 * @param field
	 * @param targetPage
	 * @param data
	 * @throws IllegalAccessException 
	 * @throws IllegalArgumentException 
	 * @throws SecurityException 
	 * @throws NoSuchMethodException 
	 * @throws InvocationTargetException 
	 */
	private void setValue(Field field, Page targetPage, Object data) throws IllegalArgumentException,
		IllegalAccessException, NoSuchMethodException, SecurityException, InvocationTargetException
	{
		if(data == null)
		{
			return;
		}
		
		field.setAccessible(true);
		Object fieldObj = field.get(targetPage);
		if(fieldObj == null)
		{
			return;
		}
		
		Method method = fieldObj.getClass().getMethod("setValue", String.class);
		String dataText = null;
		
		if(data instanceof Map && ((Map) data).containsKey("data"))
		{
			dataText = getDataFromMap((Map) data);
		}
		else if(data instanceof String)
		{
			dataText = data.toString();
		}
		else
		{
			throw new RuntimeException("Unknow data type : " + data.getClass());
		}
		
		method.invoke(fieldObj, dataText);
	}
	
	/**
	 * @param data
	 * @return
	 */
	private String getDataFromMap(Map data)
	{
		String dataText = data.get("data").toString();
		
		DynamicData dynamicData = null;
		if(data.containsKey("type"))
		{
			dynamicData = getDynamicDataByType(data.get("type").toString());
		}
		
		if(dynamicData != null)
		{
			dataText = dynamicData.getValue(dataText);
		}
		
		return dataText;
	}

	/**
	 * 根据类型获取对应的动态数据
	 * @param type
	 * @return
	 */
	private DynamicData getDynamicDataByType(String type)
	{
		for(DynamicData dynamicData : dynamicDataList)
		{
			if(dynamicData.getType().equals(type))
			{
				dynamicData.setData(globalMap);
				return dynamicData;
			}
		}
		return null;
	}

	@Override
	public void setGlobalMap(Map<String, Object> globalMap)
	{
		this.globalMap = globalMap;
	}

	@Override
	public Map<String, Object> getGlobalMap()
	{
		return globalMap;
	}

	@Override
	public String getName()
	{
		return url.getFile();
	}

}
