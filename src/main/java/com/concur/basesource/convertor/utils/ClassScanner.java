package com.concur.basesource.convertor.utils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 类文件扫描器
 * <P>支持jar格式,支持绝对路径扫描(scanPath)</P>
 * @author Jake
 *
 */
public class ClassScanner {

	/**
	 * <p>是否修正路径</p>
	 * <p>修正路径后兼容从跟路径搜索自动匹配合适的包路径</p>
	 * 如:
	 * <li>真实路径E:\\mybase\\1\\com\\XXX\\basesource</li>
	 * <li>如果输入的是E:\\mybase</li>
	 * <li>修正后则是E:\\mybase\\1\\</li>
 	 */
	private boolean fixedPath = false;

	/**
	 * 扫描包packageName中所有的Class
	 * 
	 * @param packageName 包路径 如:com.xxx
	 * @return
	 */
	public Set<Class<?>> scanPackage(String packageName) {

		ResourceDefineClassLoader classLoader = new ResourceDefineClassLoader(packageName);

		// 第一个class类的集合
		Set<Class<?>> classes = new LinkedHashSet<Class<?>>();

		try {
			Enumeration<URL> dirs = Thread.currentThread().getContextClassLoader()
					.getResources(packageName);

			while (dirs.hasMoreElements()) {
				URL url = dirs.nextElement();
				// 得到协议的名称
				String protocol = url.getProtocol();

				// 如果是以文件的形式保存在服务器上
				if ("file".equals(protocol)) {
					// 获取包的物理路径
					String filePath = URLDecoder.decode(url.getFile(), "UTF-8");
					// 以文件的方式扫描整个包下的文件 并添加到集合中
					loadFileClasses(filePath, classes, classLoader);
				} else if ("jar".equals(protocol)) {
					loadJarClasses(new File(url.getFile()), classes);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return classes;
	}


	/**
	 * 扫描文件夹所有的Class
	 *
	 * @param path 文件路径 如 E:\\
	 * @return
	 */
	public Set<Class<?>> scanPath(String path) {

		ResourceDefineClassLoader classLoader = new ResourceDefineClassLoader(path);

		// 第一个class类的集合
		Set<Class<?>> classes = new LinkedHashSet<Class<?>>();

		// 获取此包的目录 建立一个File
		File dir = new File(path);

		// 如果不存在或者 也不是目录就直接返回
		if (!dir.exists() || !dir.isDirectory()) {
			return classes;
		}

		// 如果存在 就获取包下的所有文件 包括目录
		File[] dirfiles = dir.listFiles(new FileFilter() {
			// 自定义过滤规则 如果可以循环(包含子目录) 或则是以.class结尾的文件(编译好的java类文件)
			public boolean accept(File file) {
				return file.isDirectory()
						|| file.getName().endsWith(".class")
						|| file.getName().endsWith(".jar");
			}
		});

		for (File file : dirfiles) {
			if (file.isDirectory()) {
				loadFileClasses(file.getAbsolutePath(), classes, classLoader);
			} else if (file.getName().endsWith(".jar")) {
				loadJarClasses(file, classes);
			} else {
				defineClass(classes, file, classLoader);
			}
		}

		return classes;
	}


	/**
	 * 加载Jar的类文件
	 * @param file File
	 * @param classes class Set
	 */
	private void loadJarClasses(File file, Set<Class<?>> classes) {

		// 如果是jar包文件
		try {
			//自己定义的classLoader类，把外部路径也加到load路径里，使系统去该路经load对象
			URLClassLoader loader = new URLClassLoader(new URL[]{file.toURL()});

            // 获取jar
            JarFile jar = new JarFile(file);
            // 从此jar包 得到一个枚举类
            Enumeration<JarEntry> entries = jar.entries();
            // 同样的进行循环迭代
            while (entries.hasMoreElements()) {
                // 获取jar里的一个实体 可以是目录 和一些jar包里的其他文件 如META-INF等文件
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                // 如果是一个.class文件 而且不是目录
                if (name.endsWith(".class") && !entry.isDirectory()) {
                    // 去掉后面的".class" 获取真正的类名
                    try {
                        //自己定义的loader路径可以找到
                        Class<?> clazz = loader.loadClass(name.replace("/", ".").substring(0,name.length() - 6));
                        classes.add(clazz);
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
	}


	/**
	 * 以文件的形式来获取包下的所有Class
	 * 
	 * @param packagePath packagePath
	 * @param classes class Set
	 */
	public void loadFileClasses(String packagePath, Set<Class<?>> classes, ResourceDefineClassLoader classLoader) {
		// 获取此包的目录 建立一个File
		File dir = new File(packagePath);

		// 如果不存在或者 也不是目录就直接返回
		if (!dir.exists() || !dir.isDirectory()) {
			return;
		}

		// 如果存在 就获取包下的所有文件 包括目录
		File[] dirfiles = dir.listFiles(new FileFilter() {
			// 自定义过滤规则 如果可以循环(包含子目录) 或则是以.class结尾的文件(编译好的java类文件)
			public boolean accept(File file) {
				return file.isDirectory() || file.getName().endsWith(".class");
			}
		});

		for (File file : dirfiles) {
			if (file.isDirectory()) {
				loadFileClasses(file.getAbsolutePath(), classes, classLoader);
			} else {
				defineClass(classes, file, classLoader);
			}
		}
	}

	/**
	 * 解析类
	 * @param classes classes 类缓存容器
	 * @param file File
	 * @param classLoader 类加载器
	 */
	private void defineClass(Set<Class<?>> classes, File file, ResourceDefineClassLoader classLoader) {
		ClassMeta classMeta = ClassMetaUtil.getClassMeta(file);
		if (classMeta == null) {
			return;
		}
		Class<?> clazz = classLoader.loadClass(classMeta.getClassName(), classMeta.getBytes());
		classes.add(clazz);

		if (!this.fixedPath) {

			String className = classMeta.getClassName();
			String classDirectory = className.replace(".", File.separator);
			classDirectory = classDirectory.substring(0, classDirectory.lastIndexOf(File.separator));

			String absPath = file.getAbsolutePath();
			absPath = absPath.substring(0, absPath.lastIndexOf(File.separator));

			int overlapping = absPath.lastIndexOf(classDirectory);
			if (overlapping > -1) {
				String fixedPath = absPath.substring(0, overlapping);
				classLoader.setBasePath(fixedPath);
			}
			this.fixedPath = true;
		}

	}


}
