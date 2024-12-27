package com.example.chatroom;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ChatClientGUI extends JFrame {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 8888;
    
    private JTextPane chatPane;
    private JTextArea messageArea;
    private JButton sendButton;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private Socket socket;
    private PrintWriter writer;
    private String username;
    private StyledDocument doc;
    private final Color MY_MESSAGE_COLOR = new Color(225, 255, 225);
    private final Color OTHER_MESSAGE_COLOR = new Color(255, 255, 255);
    
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
        
        // 聊天记录区域使用JTextPane
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        doc = chatPane.getStyledDocument();
        JScrollPane chatScroll = new JScrollPane(chatPane);
        chatScroll.setBorder(BorderFactory.createTitledBorder("聊天记录"));
        
        // 创建底部消息发送区域
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        messageArea = new JTextArea(3, 20); // 3行高的文本区域
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        JScrollPane messageScroll = new JScrollPane(messageArea);
        
        sendButton = new JButton("发送");
        sendButton.setPreferredSize(new Dimension(80, 50));
        
        bottomPanel.add(messageScroll, BorderLayout.CENTER);
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
        
        // Ctrl+Enter发送消息
        messageArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isControlDown()) {
                    sendMessage();
                    e.consume(); // 防止换行符被添加
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
    
    private void appendMessage(String sender, String message, boolean isMyMessage) {
        SwingUtilities.invokeLater(() -> {
            try {
                // 创建气泡样式
                Style style = chatPane.addStyle("BubbleStyle", null);
                StyleConstants.setBackground(style, isMyMessage ? MY_MESSAGE_COLOR : OTHER_MESSAGE_COLOR);
                StyleConstants.setForeground(style, Color.BLACK);
                StyleConstants.setFontSize(style, 14);
                
                // 添加时间和发送者
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                String time = sdf.format(new Date());
                String header = String.format("[%s] %s\n", time, sender);
                
                // 设置对齐方式
                StyleConstants.setAlignment(style, isMyMessage ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);
                
                // 插入消息
                doc.insertString(doc.getLength(), header, style);
                doc.insertString(doc.getLength(), message + "\n\n", style);
                
                // 滚动到底部
                chatPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }
    
    private void receiveMessages() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String message;
            while ((message = reader.readLine()) != null) {
                final String finalMessage = message;
                if (finalMessage.startsWith("@USERLIST@")) {
                    SwingUtilities.invokeLater(() -> updateUserList(finalMessage.substring(10)));
                } else {
                    // 解析消息来源
                    String sender;
                    String content;
                    if (finalMessage.contains(": ")) {
                        sender = finalMessage.substring(0, finalMessage.indexOf(": "));
                        content = finalMessage.substring(finalMessage.indexOf(": ") + 2);
                    } else {
                        sender = "系统消息";
                        content = finalMessage;
                    }
                    boolean isMyMessage = sender.equals(username);
                    appendMessage(sender, content, isMyMessage);
                }
            }
        } catch (IOException e) {
            if (!socket.isClosed()) {
                JOptionPane.showMessageDialog(this, "与服务器的连接已断开！", "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void sendMessage() {
        String message = messageArea.getText().trim();
        if (!message.isEmpty()) {
            writer.println(message);
            messageArea.setText("");
        }
        messageArea.requestFocus();
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
    
    private void connectToServer() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            writer = new PrintWriter(socket.getOutputStream(), true);
            
            // 获取用户名
            while (true) {
                username = JOptionPane.showInputDialog(this, 
                    "请输入你的用户名：", 
                    "登录", 
                    JOptionPane.QUESTION_MESSAGE);
                    
                if (username == null) {
                    System.exit(0); // 用户点击取消按钮
                }
                
                username = username.trim();
                if (!username.isEmpty()) {
                    break;
                }
                
                JOptionPane.showMessageDialog(this, 
                    "用户名不能为空！", 
                    "错误", 
                    JOptionPane.ERROR_MESSAGE);
            }
            
            // 设置窗口标题包含用户名
            setTitle("聊天室 - " + username);
            
            // 发送用户名到服务器
            writer.println(username);
            
            // 启动消息接收线程
            new Thread(this::receiveMessages).start();
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, 
                "无法连接到服务器！\n" + e.getMessage(), 
                "连接错误", 
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
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