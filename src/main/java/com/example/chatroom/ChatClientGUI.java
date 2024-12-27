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
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Base64;
import javax.swing.filechooser.FileNameExtensionFilter;

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
    private JButton imageButton;
    private final int MAX_IMAGE_SIZE = 800;

    private final UserDao userDao = new UserDao();
    
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
        messageArea = new JTextArea(3, 20);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        JScrollPane messageScroll = new JScrollPane(messageArea);
        
        // 创建按钮面板
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        sendButton = new JButton("发送");
        imageButton = new JButton("图片");
        sendButton.setPreferredSize(new Dimension(80, 25));
        imageButton.setPreferredSize(new Dimension(80, 25));
        buttonPanel.add(sendButton);
        buttonPanel.add(imageButton);
        
        bottomPanel.add(messageScroll, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        
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
        
        // 添加图片按钮事件
        imageButton.addActionListener(e -> sendImage());
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
                
                // 插入消息，将特殊标记替换回换行符
                doc.insertString(doc.getLength(), header, style);
                // 在 lambda 内部创建新的字符串，而不是修改参数
                String processedMessage = message.replaceAll("@LINE_BREAK@", "\n");
                doc.insertString(doc.getLength(), processedMessage + "\n\n", style);
                
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
            StringBuilder imageBuilder = null;
            String imageSender = null;
            int expectedChunks = 0;
            int receivedChunks = 0;
            
            while ((message = reader.readLine()) != null) {
                if (message.startsWith("@USERLIST@")) {
                    final String userListMessage = message;
                    SwingUtilities.invokeLater(() -> updateUserList(userListMessage.substring(10)));
                } 
                else {
                    final String currentMessage = message;
                    String sender = "";
                    String content = currentMessage;
                    
                    if (currentMessage.contains(": ")) {
                        sender = currentMessage.substring(0, currentMessage.indexOf(": "));
                        content = currentMessage.substring(currentMessage.indexOf(": ") + 2);
                    }
                    
                    final String finalSender = sender;
                    final String finalContent = content;
                    
                    // 处理图片消息
                    if (content.startsWith("@IMAGE_START@")) {
                        try {
                            // 直接从内容中提取数字部分
                            String numStr = content.replaceAll("[^0-9]", "");
                            expectedChunks = Integer.parseInt(numStr);
                            imageBuilder = new StringBuilder();
                            receivedChunks = 0;
                            imageSender = sender;
                            System.out.println("开始接收图片，预期块数：" + expectedChunks); // 调试信息
                        } catch (NumberFormatException e) {
                            System.err.println("无法解析图片块数量: " + content);
                            imageBuilder = null;
                            imageSender = null;
                        }
                    }
                    else if (content.startsWith("@IMAGE_CHUNK@") && imageBuilder != null) {
                        try {
                            String imageData = content.substring(13);
                            imageBuilder.append(imageData);
                            receivedChunks++;
                            System.out.println("接收到图片块：" + receivedChunks + "/" + expectedChunks); // 调试信息
                            
                            // 如果收到所有分块，则显示图片
                            if (receivedChunks == expectedChunks) {
                                String base64Image = imageBuilder.toString();
                                final boolean isMyMessage = imageSender.equals(username);
                                String finalImageSender = imageSender;
                                SwingUtilities.invokeLater(() ->
                                    appendImage(
                                            finalImageSender,
                                        base64Image, 
                                        isMyMessage
                                    )
                                );
                                imageBuilder = null;
                                imageSender = null;
                                System.out.println("图片接收完成"); // 调试信息
                            }
                        } catch (Exception e) {
                            System.err.println("处理图片块时出错: " + e.getMessage());
                            imageBuilder = null;
                            imageSender = null;
                        }
                    }
                    else if (content.startsWith("@IMAGE_END@")) {
                        // 重置图片构建器
                        imageBuilder = null;
                        imageSender = null;
                        System.out.println("图片传输结束"); // 调试信息
                    }
                    else if (!content.startsWith("@IMAGE")) {
                        // 处理普通文本消息
                        final boolean isMyMessage = finalSender.equals(username);
                        SwingUtilities.invokeLater(() -> 
                            appendMessage(
                                finalSender.isEmpty() ? "系统消息" : finalSender, 
                                finalContent, 
                                isMyMessage
                            )
                        );
                    }
                }
            }
        } catch (IOException e) {
            if (!socket.isClosed()) {
                JOptionPane.showMessageDialog(this, 
                    "与服务器的连接已断开！", 
                    "错误", 
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private boolean authenticateUser(String username, String password) {
        try{
            UserDao.User user = userDao.getUserByName(username);
            if (user == null) {
                // 用户不存在
                JOptionPane.showMessageDialog(this, "用户不存在", "登录失败", JOptionPane.WARNING_MESSAGE);
            } else if (!password.equals(user.getPassword())) {
                // 密码不匹配
                JOptionPane.showMessageDialog(this, "用户密码错误", "登录失败", JOptionPane.WARNING_MESSAGE);
            } else {
                // 登录成功
                JOptionPane.showMessageDialog(this, "登录成功", "登录成功", JOptionPane.INFORMATION_MESSAGE);
                return true;
            }
            return false;
        } catch (Exception e){
            System.out.println(e.getMessage());
        }
        return false;
    }

    private String showPasswordDialog(Component parent) {
        // 创建 JPasswordField 组件
        JPasswordField passwordField = new JPasswordField();

        // 设置为不可编辑，以防止复制粘贴等操作
        passwordField.setEditable(true);

        // 创建并配置 JOptionPane 使用自定义输入字段
        int option = JOptionPane.showConfirmDialog(
                parent,
                passwordField,
                "请输入密码",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        // 如果用户点击了 OK 按钮，则返回密码字符串；否则返回 null
        if (option == JOptionPane.OK_OPTION) {
            return new String(passwordField.getPassword());
        } else {
            return null;
        }
    }
    
    private void sendMessage() {
        String message = messageArea.getText().trim();
        if (!message.isEmpty()) {
            // 将所有换行符替换为特殊标记
            message = message.replaceAll("\n", "@LINE_BREAK@");
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
            while(true){
                username = JOptionPane.showInputDialog(this, "请输入你的用户名：", "登录", JOptionPane.QUESTION_MESSAGE);
                if (username == null) {
                    System.exit(0);
                }
                username = username.trim();
                if(username.isEmpty()){
                    JOptionPane.showMessageDialog(this, "用户名不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
                    continue;
                }
                // 如果以#开头则跳过密码认证
                if (!username.startsWith("#")){
                    String password = showPasswordDialog(this);
                    if (password == null) {
                        System.exit(0);
                    }
                    if(authenticateUser(username, password)){
                        break;
                    }
                }
                else{
                    username = username.substring(1);
                    break;
                }
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
    
    private void sendImage() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter(
            "图片文件", "jpg", "jpeg", "png", "gif"));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = fileChooser.getSelectedFile();
                // 检查文件扩展名
                String fileName = file.getName().toLowerCase();
                if (!fileName.endsWith(".jpg") && !fileName.endsWith(".jpeg") 
                    && !fileName.endsWith(".png") && !fileName.endsWith(".gif")) {
                    JOptionPane.showMessageDialog(this,
                        "请选择正确的图片文件格式(jpg, jpeg, png, gif)！",
                        "格式错误",
                        JOptionPane.WARNING_MESSAGE);
                    return;
                }
                
                BufferedImage originalImage = ImageIO.read(file);
                if (originalImage == null) {
                    JOptionPane.showMessageDialog(this,
                        "所选文件不是有效的图片文件！",
                        "格式错误",
                        JOptionPane.WARNING_MESSAGE);
                    return;
                }
                
                // 调整图片大小
                BufferedImage resizedImage = resizeImageIfNeeded(originalImage);
                
                // 将图片转换为Base64字符串
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(resizedImage, "png", baos);
                byte[] imageBytes = baos.toByteArray();
                String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                
                // 分块发送（每块最大32KB）
                int chunkSize = 32 * 1024;
                int chunks = (base64Image.length() + chunkSize - 1) / chunkSize;
                
                System.out.println("开始发送图片，总块数：" + chunks); // 调试信息
                
                // 发送图片开始标记和总块数
                writer.println("@IMAGE_START@" + chunks);
                
                // 分块发送图片数据
                for (int i = 0; i < chunks; i++) {
                    int start = i * chunkSize;
                    int end = Math.min(start + chunkSize, base64Image.length());
                    String chunk = base64Image.substring(start, end);
                    writer.println("@IMAGE_CHUNK@" + chunk);
                    System.out.println("发送图片块：" + (i + 1) + "/" + chunks); // 调试信息
                    
                    // 添加小延迟避免消息���积
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                // 发送图片结束标记
                writer.println("@IMAGE_END@");
                System.out.println("图片发送完成"); // 调试信息
                
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, 
                    "无法发送图片：" + e.getMessage(), 
                    "错误", 
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private BufferedImage resizeImageIfNeeded(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();
        
        if (width <= MAX_IMAGE_SIZE && height <= MAX_IMAGE_SIZE) {
            return original;
        }
        
        if (width > height) {
            float ratio = (float) MAX_IMAGE_SIZE / width;
            width = MAX_IMAGE_SIZE;
            height = Math.round(height * ratio);
        } else {
            float ratio = (float) MAX_IMAGE_SIZE / height;
            height = MAX_IMAGE_SIZE;
            width = Math.round(width * ratio);
        }
        
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, 
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, width, height, null); 
        g.dispose();
        
        return resized;
    }
    
    private void appendImage(String sender, String base64Image, boolean isMyMessage) {
        SwingUtilities.invokeLater(() -> {
            try {
                // 解码Base64图片
                byte[] imageBytes = Base64.getDecoder().decode(base64Image);
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
                
                if (image == null) {
                    appendMessage(sender, "[图片发送失败]", isMyMessage);
                    return;
                }
                
                // 创建样式
                Style style = chatPane.addStyle("ImageStyle", null);
                StyleConstants.setAlignment(style, 
                    isMyMessage ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);
                StyleConstants.setBackground(style, 
                    isMyMessage ? MY_MESSAGE_COLOR : OTHER_MESSAGE_COLOR);
                
                // 添加时间和发送者
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                String time = sdf.format(new Date());
                String header = String.format("[%s] %s\n", time, sender);
                
                // 插入发送者信息
                doc.insertString(doc.getLength(), header, style);
                
                // 创建带背景色的面板来容纳图片
                JPanel imagePanel = new JPanel();
                imagePanel.setBackground(isMyMessage ? MY_MESSAGE_COLOR : OTHER_MESSAGE_COLOR);
                imagePanel.add(new JLabel(new ImageIcon(image)));
                
                // 将面板作为自定义视图插入
                Style labelStyle = chatPane.addStyle("LabelStyle", null);
                StyleConstants.setComponent(labelStyle, imagePanel);
                doc.insertString(doc.getLength(), " ", labelStyle);
                doc.insertString(doc.getLength(), "\n\n", style);
                
                // 滚动到底部
                chatPane.setCaretPosition(doc.getLength());
                
            } catch (Exception e) {
                e.printStackTrace();
                appendMessage(sender, "[图片显示失败]", isMyMessage);
            }
        });
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