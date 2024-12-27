package com.example.chatroom;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private String username;
    private ConcurrentHashMap<String, ClientHandler> clients;

    public ClientHandler(Socket socket, ConcurrentHashMap<String, ClientHandler> clients) {
        this.socket = socket;
        this.clients = clients;
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            username = reader.readLine();
            clients.put(username, this);
            broadcast(username + " 加入了聊天室！");
            updateUserList();

            String message;
            while ((message = reader.readLine()) != null) {
                if (message.equals("exit")) {
                    break;
                }
                broadcast(username + ": " + message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            disconnect();
        }
    }

    private void broadcast(String message) {
        System.out.println(message);
        clients.values().forEach(client -> client.sendMessage(message));
    }

    private void updateUserList() {
        StringBuilder userList = new StringBuilder("@USERLIST@");
        clients.keySet().forEach(user -> userList.append(user).append(","));
        broadcast(userList.toString());
    }

    private void sendMessage(String message) {
        writer.println(message);
    }

    private void disconnect() {
        if (username != null) {
            clients.remove(username);
            broadcast(username + " 离开了聊天室！");
            updateUserList();
        }
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
} 