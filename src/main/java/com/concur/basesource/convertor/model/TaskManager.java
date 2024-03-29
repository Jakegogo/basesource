package com.concur.basesource.convertor.model;

import com.concur.basesource.convertor.MainApp;
import com.concur.basesource.convertor.task.ConvertTask;
import com.concur.basesource.convertor.task.TaskStatus;
import com.concur.basesource.storage.StorageManager;
import com.concur.unity.thread.NamedThreadFactory;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 任务管理器
 * Created by Jake on 2015/6/13.
 */
public class TaskManager {

    /** 任务执行线程输 */
    private static final int taskThreadSize = 2;

    /**
     * 任务执行线程池
     */
    private ExecutorService DB_POOL_SERVICE;

    /**
     * 当前任务
     */
    private volatile ConvertTask curTask;

    /**
     * 文件表格数据模型
     */
    private ProgressTableModel tableModel;

    /**
     * 基础数据缓存管理器
     */
    private StorageManager storageManager;


    public TaskManager(ProgressTableModel tableModel) {
        this.tableModel = tableModel;
        this.init();
    }

    private void init() {
        // 初始化线程池
        ThreadGroup threadGroup = new ThreadGroup("基础数据表转换模块");
        NamedThreadFactory threadFactory = new NamedThreadFactory(threadGroup, "转换任务线程池");

        DB_POOL_SERVICE = Executors.newFixedThreadPool(taskThreadSize, threadFactory);

        this.storageManager = MainApp.getApplicationContext().getAutowireCapableBeanFactory().createBean(StorageManager.class);

    }


    /**
     * 开始任务
     */
    public void start(TaskStatusChangeCallback completeCallback) {
        DB_POOL_SERVICE.submit(createRunnable(completeCallback));
    }


    /**
     * 创建任务Runnable
     * @param completeCallback TaskStatusChangeCallback
     * @return
     */
    private Runnable createRunnable(final TaskStatusChangeCallback completeCallback) {

        final TaskStatusChangeCallback wrapperCompleteCallback = new TaskStatusChangeCallback() {
            @Override
            public void onStart() {
                storageManager.clear();// 清除所有基础数据缓存
                completeCallback.onStart();
            }

            @Override
            public void onComplete() {
                completeCallback.onComplete();
                curTask = null;// 删除任务
            }
        };

        return new Runnable() {
            @Override
            public void run() {
                // 不存在则创建新的任务
                if (curTask == null) {
                    File inputPath = UserConfig.getInstance().getInputPath();
                    curTask = new ConvertTask(storageManager, inputPath, tableModel);
                    curTask.start(wrapperCompleteCallback);
                } else {
                    curTask.start(wrapperCompleteCallback);
                }
            }
        };
    }


    /**
     * 暂停当前任务
     * @return
     */
    public boolean stop() {
        if (curTask == null) {
            return false;
        }
        curTask.stop();
        return true;
    }


    /**
     * 取消当前任务
     * 重置任务进度且销毁当前任务(避免多线程问题)
     */
    public boolean cancel() {
        if (curTask == null) {
            return false;
        }
        curTask.reset();
        curTask = null;
        return true;
    }


    /**
     * 获取任务状态
     * @return
     */
    public TaskStatus getStatus() {
        if (curTask == null) {
            return TaskStatus.INIT;
        }
        return curTask.getStatus();
    }

    /**
     * 是否为暂停状态
     * @return
     */
    public boolean isStarted() {
        if (curTask == null) {
            return false;
        }
        return curTask.getStatus() == TaskStatus.STARTED;
    }

    /**
     * 改变输入路径
     * @param path
     */
    public void changeInputPath(File path) {
        if (curTask != null) {
            curTask.changeInputPath(path);
        }
    }


}
