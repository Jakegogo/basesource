package com.concur.basesource.convertor.ui;

import com.concur.basesource.convertor.contansts.DefaultUIConstant;
import com.concur.basesource.convertor.model.UserConfig;
import com.concur.basesource.convertor.ui.extended.HintTextField;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

/**
 * 设置面板
 * Created by Jake on 2015/6/13.
 */
public class OptionPanel extends JPanel {


    public OptionPanel() {
        this.init();
    }

    private void init() {

        // 构建布局
        GridBagLayout gridBagLayout = new GridBagLayout();
        this.setLayout(gridBagLayout);

        JLabel openSelectPathButton = new JLabel(
                DefaultUIConstant.CODE_INPUT_PATH_TIP,
                new ImageIcon(getClass().getResource("resources/images/code.png")),
                JLabel.RIGHT);

        // 路径文件输入框
        final JTextField textField = new HintTextField(DefaultUIConstant.CODE_INPUT_PATH_TIP);
        textField.setEnabled(false);

        add(openSelectPathButton);
        gridBagLayout.setConstraints(openSelectPathButton,
                new GridBagConstraints(0,0,1,1,1,1,
                        GridBagConstraints.WEST,
                        GridBagConstraints.BOTH,
                        new Insets(0, 0, 0, 0), 0,0));

        add(textField);
        gridBagLayout.setConstraints(textField,
                new GridBagConstraints(1,0,6,1,5,1,
                        GridBagConstraints.WEST,
                        GridBagConstraints.BOTH,
                        new Insets(0, 0, 0, 0), 0,0));


        // 构建文件选择器
        final JFileChooser fileChooser = this.createFileChooser();
        textField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int retval = fileChooser.showOpenDialog(textField);

                if (retval != JFileChooser.APPROVE_OPTION) {
                    return;
                }

                File selectedFile = fileChooser.getSelectedFile();
                if (selectedFile != null && selectedFile.exists()) {
                    UserConfig.getInstance().changeSourceDefineInputPath(selectedFile);
                    textField.setText(selectedFile.getAbsolutePath());
                }
            }
        });


        // 初始化显示路径
        File sourceDefineInputPath = UserConfig.getInstance().getSourceDefineInputPath();
        if (sourceDefineInputPath != null && sourceDefineInputPath.exists()) {
            textField.setText(sourceDefineInputPath.getAbsolutePath());
        }

    }


    /**
     * 构建文件选择器
     * @return
     */
    private JFileChooser createFileChooser() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory();
            }

            @Override
            public String getDescription() {
                return DefaultUIConstant.ACCECPT_FILE_LIMIT_TIP;
            }
        });
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);//只能选择目录
        return fileChooser;
    }

}
