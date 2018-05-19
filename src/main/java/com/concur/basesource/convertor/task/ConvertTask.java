package com.concur.basesource.convertor.task;

import com.concur.basesource.convertor.model.ProgressTableModel;
import com.concur.basesource.convertor.model.TaskStatusChangeCallback;
import com.concur.basesource.convertor.model.UserConfig;
import com.concur.basesource.convertor.utils.ClassScanner;
import com.concur.basesource.convertor.utils.SheetInfo;
import com.concur.basesource.convertor.utils.SheetUtils;
import com.concur.basesource.storage.FormatDefinition;
import com.concur.basesource.storage.ResourceDefinition;
import com.concur.basesource.storage.Storage;
import com.concur.basesource.storage.StorageManager;
import com.concur.unity.utils.JsonUtils;
import org.apache.commons.io.FileUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * 转换任务
 * Created by Jake on 2015/6/11.
 */
public class ConvertTask implements ProgressMonitorable {

    /**
     * 文件夹路径
     */
    File path;

    /**
     * 表格模型
     */
    ProgressTableModel tableModel;

    /**
     * 当前进度的文件迭代
     */
    private Iterator<File> cur;

    /**
     * 当前任务序号 从0开始
     */
    private volatile int curTaskIndex = 0;

    /**
     * 基础数据缓存管理器
     */
    private StorageManager storageManager;

    /**
     * 是否暂停
     */
    private volatile TaskStatus status = TaskStatus.INIT;


    /**
     * 构造方法
     *
     * @param storageManager StorageManager
     * @param path           扫描路径
     * @param tableModel     表格数据模型
     */
    public ConvertTask(StorageManager storageManager, File path, ProgressTableModel tableModel) {
        this.path = path;
        this.tableModel = tableModel;
        this.storageManager = storageManager;
    }


    /**
     * 开始任务
     */
    public void start(TaskStatusChangeCallback completeCallback) {
        // 任务已经开始了
        if (status == TaskStatus.STARTED) {
            return;
        }
        // 改变任务状态
        status = TaskStatus.STARTED;
        // 状态改变回调
        completeCallback.onStart();

        // 读取基础数据定义类
        Map<String, Class<?>> loadedClassMap = loadCodeSource();

        // 创建迭代器
        if (cur == null) {
            cur = tableModel.getSortedRowFiles().iterator();
            curTaskIndex = 0;

            // 重置进度
            tableModel.clearProgress();
        }

        // 开始转换
        while (cur.hasNext()) {
            if (status != TaskStatus.STARTED) {
                return;
            }
            File file = cur.next();
            try {
                runSubTask(file, loadedClassMap);
            } catch (RuntimeException e) {
                System.err.println("文件转换失败:" + file.getName());
                e.printStackTrace();
            }
            // 自增任务序号
            curTaskIndex++;
        }

        // 验证数据 TODO


        if (status == TaskStatus.STARTED) {
            // 任务结束回调
            completeCallback.onComplete();
            status = TaskStatus.FINISHED;
        }
        // 销毁迭代器
        cur = null;
    }


    /**
     * 暂停任务
     */
    public void stop() {
        status = TaskStatus.STOPED;
    }

    /**
     * 是否为暂停状态
     *
     * @return
     */
    public boolean isStop() {
        return status == TaskStatus.STOPED || status == TaskStatus.CANCEL;
    }

    /**
     * 重置任务
     */
    public void reset() {
        status = TaskStatus.CANCEL;
        curTaskIndex = 0;

        // 重置迭代器
        cur = null;

        // 重置进度
        tableModel.clearProgress();
    }

    /**
     * 获取任务状态
     *
     * @return
     */
    public TaskStatus getStatus() {
        return status;
    }

    /**
     * 改变输入路径
     *
     * @param path
     */
    public void changeInputPath(File path) {
        this.reset();
        this.path = path;

        // 重置进度
        tableModel.clearProgress();
    }

    /**
     * 开始
     *
     * @param file
     */
    private void runSubTask(File file, Map<String, Class<?>> loadedClassMap) {

        Workbook workbook = getWorkbook(file);
        updateProgress(0.37d);
        Map<String, SheetInfo> sheets = SheetUtils.listSheets(workbook, file);

        int size = sheets.size();// 总Sheet数量
        int curSheetIndex = 0;// 当前转换的Sheet序号

        for (Map.Entry<String, SheetInfo> entry : sheets.entrySet()) {
            String name = entry.getKey();
            SheetInfo sheetInfo = entry.getValue();

            try {

                if (loadedClassMap.containsKey(name)) {
                    // 有基础数据类定义的
                    Class<?> cls = loadedClassMap.get(name);
                    // 创建基础数据资源定义
                    ResourceDefinition resourceDefinition = createResourceDefinition(cls, loadedClassMap, name, sheetInfo.file);
                    // 初始化基础数据定义
                    this.storageManager.initialize(resourceDefinition);

                    Storage<?, ?> storage = this.storageManager.getStorage(cls);
                    Collection<?> beanList = storage.getAll();

                    this.writeFile(name, beanList);
                } else {
                    // 直接转换
                    // 创建数据集
                    List<Map<String, String>> beanList = SheetUtils.readSheetData(sheetInfo,
                            ((double) curSheetIndex + 1) / size * (1 - 0.37d) + 0.37d,
                            new SheetUtils.ProgressAware() {
                                @Override
                                public void onProgress(double percent) {
                                    updateProgress(percent);
                                }
                            });

                    this.writeFile(name, beanList);
                }

                curSheetIndex++;
                updateProgress(((double) curSheetIndex) / size * (1 - 0.37d) + 0.37d);
            } catch (RuntimeException e) {
                markAsFail(name, e);
                System.err.println("表格转换异常:" + name);
                e.printStackTrace();
            }
        }

        updateProgress(1d);
    }


    // 改变转换进度
    @Override
    public void updateProgress(double v) {
        if (this.getStatus() == TaskStatus.STARTED || this.getStatus() == TaskStatus.STOPED) {
            this.tableModel.changeProgress(curTaskIndex, v);
        }
    }

    // 标记为任务失败
    public void markAsFail(String name, Exception e) {
        if (this.getStatus() == TaskStatus.STARTED || this.getStatus() == TaskStatus.STOPED) {
            this.tableModel.markAsFail(curTaskIndex, name, e);
        }
    }


    // 保存到文件
    private void writeFile(String name, Object beanList) {
        // 转换成json
        String content = JsonUtils.object2PrettyJsonString(beanList);

        String path = UserConfig.getInstance().getOutputPath().getAbsolutePath();
        String fileName = path + File.separator + name + ".json";
        try {
            FileUtils.writeStringToFile(new File(fileName), content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // 创建基础数据资源定义
    private ResourceDefinition createResourceDefinition(Class<?> cls, Map<String, Class<?>> loadedClassMap, String name, File file) {
        // 获取后缀
        String fileName = file.getName();
        String fileType = fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length());

        // 获取定义
        FormatDefinition formatDefinition = new FormatDefinition("file:" + file.getAbsolutePath(), "excel", fileType);
        return new ResourceDefinition(cls, formatDefinition, null);
    }


    /**
     * 获取基础数据定义的类
     *
     * @return
     */
    private Map<String, Class<?>> loadCodeSource() {
        Map<String, Class<?>> loadedClassMap = new HashMap<String, Class<?>>();
        File sourceDefineInputPath = UserConfig.getInstance().getSourceDefineInputPath();
        if (sourceDefineInputPath != null && sourceDefineInputPath.exists()) {
            ClassScanner classScanner = new ClassScanner();
            Set<Class<?>> loadedClass = classScanner.scanPath(sourceDefineInputPath.getAbsolutePath());

            loadedClassMap = new HashMap<String, Class<?>>();
            for (Class<?> cls : loadedClass) {
                loadedClassMap.put(cls.getSimpleName(), cls);
            }
        }
        return loadedClassMap;
    }

    /**
     * 通过输入流获取{@link Workbook}
     *
     * @param file
     * @return
     */
    public static Workbook getWorkbook(File file) {
        try {
            return WorkbookFactory.create(new ProgressMonitorInputStream(new FileInputStream(file), 0.1f) {
                @Override
                public void updateProgress(double progresss) {
                    updateProgress(0.37d * progresss);
                }
            });
        } catch (InvalidFormatException e) {
            throw new RuntimeException("静态资源[" + file.getPath() + "]异常,无效的文件格式", e);
        } catch (IOException e) {
            throw new RuntimeException("静态资源[" + file.getPath() + "]异常,无法读取文件", e);
        }
    }


    public File getPath() {
        return path;
    }


}
