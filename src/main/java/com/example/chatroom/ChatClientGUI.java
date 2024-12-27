package com.example.chatroom;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;

public class ChatClientGUI extends JFrame {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 8888;
    
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private Socket socket;
    private PrintWriter writer;
    private String username;
    
    public ChatClientGUI() {
        // 设置窗口基本属性
        setTitle("聊天室");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // 创建左侧面板（用户列表）
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setPreferredSize(new Dimension(200, 0));
        JScrollPane userListScroll = new JScrollPane(userList);
        userListScroll.setBorder(BorderFactory.createTitledBorder("在线用户"));
        
        // 创建右侧主聊天区域
        JPanel chatPanel = new JPanel(new BorderLayout(5, 5));
        
        // 聊天记录区域
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createTitledBorder("聊天记录"));
        
        // 创建底部消息发送区域
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        messageField = new JTextField();
        sendButton = new JButton("发送");
        sendButton.setPreferredSize(new Dimension(80, 30));
        
        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        
        // 组装聊天面板
        chatPanel.add(chatScroll, BorderLayout.CENTER);
        chatPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        // 组装主面板
        mainPanel.add(userListScroll, BorderLayout.WEST);
        mainPanel.add(chatPanel, BorderLayout.CENTER);
        
        // 添加到窗口
        add(mainPanel);
        
        // 添加事件监听
        setupEventListeners();
        
        // 连接服务器
        connectToServer();
    }
    
    private void setupEventListeners() {
        // 发送按钮点击事件
        sendButton.addActionListener(e -> sendMessage());
        
        // 输入框回车事件
        messageField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendMessage();
                }
            }
        });
        
        // 窗口关闭事件
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });
    }
    
    private void connectToServer() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            writer = new PrintWriter(socket.getOutputStream(), true);
            
            // 获取用户名
            username = JOptionPane.showInputDialog(this, "请输入你的用户名：", "登录", JOptionPane.QUESTION_MESSAGE);
            if (username == null || username.trim().isEmpty()) {
                System.exit(0);
            }
            
            writer.println(username);
            
            // 启动消息接收线程
            new Thread(this::receiveMessages).start();
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "无法连接到服务器！", "错误", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
    
    private void receiveMessages() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String message;
            while ((message = reader.readLine()) != null) {
                final String finalMessage = message;
                SwingUtilities.invokeLater(() -> {
                    if (finalMessage.startsWith("@USERLIST@")) {
                        // 更新用户列表
                        updateUserList(finalMessage.substring(10));
                    } else {
                        // 显示普通消息
                        chatArea.append(finalMessage + "\n");
                        // 自动滚动到底部
                        chatArea.setCaretPosition(chatArea.getDocument().getLength());
                    }
                });
            }
        } catch (IOException e) {
            if (!socket.isClosed()) {
                JOptionPane.showMessageDialog(this, "与服务器的连接已断开！", "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            writer.println(message);
            messageField.setText("");
        }
        messageField.requestFocus();
    }
    
    private void disconnect() {
        if (writer != null) {
            writer.println("exit");
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void updateUserList(String userListStr) {
        userListModel.clear();
        String[] users = userListStr.split(",");
        for (String user : users) {
            if (!user.trim().isEmpty()) {
                userListModel.addElement(user);
            }
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // 设置本地系统外观
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new ChatClientGUI().setVisible(true);
        });
    }
} 