package com.example.chatappfirebase.activities;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.example.chatappfirebase.adapters.RecentConversationAdapter;
import com.example.chatappfirebase.databinding.ActivityMainBinding;
import com.example.chatappfirebase.listeners.ConversionListener;
import com.example.chatappfirebase.models.ChatMessage;
import com.example.chatappfirebase.models.User;
import com.example.chatappfirebase.utilities.Constants;
import com.example.chatappfirebase.utilities.PreferenceManager;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
// Màn hình chính của ứng dụng chat, hiển thị danh sách các cuộc trò chuyện gần đây của người dùng.
// File quản lý thông tin người dùng, xử lý đăng xuất, yêu cầu quyền thông báo, và lắng nghe các thay đổi trong
// cuộc trò chuyện từ Firestore. Nó kế thừa từ BaseActivity để quản lý trạng thái trực tuyến và triển khai giao diện
// ConversionListener để xử lý sự kiện khi người dùng nhấp vào một cuộc trò chuyện.
public class MainActivity extends BaseActivity implements ConversionListener {

    private ActivityMainBinding binding;
    private PreferenceManager preferenceManager;

    // Danh sách các cuộc trò chuyện gần đây
    private List<ChatMessage> conversations;

    // Adapter để hiển thị danh sách cuộc trò chuyện trong RecyclerView
    private RecentConversationAdapter conversationAdapter;
    private FirebaseFirestore database;

    // Xử lý yêu cầu quyền thông báo (cho Android 13+)
    private final ActivityResultLauncher<String> requestNotificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {

                } else {
                    showToast("Permission denied");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            String permission = Manifest.permission.POST_NOTIFICATIONS;
            if (ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {

            } else if (shouldShowRequestPermissionRationale(permission)) {

            } else {
                requestNotificationPermission.launch(permission);
            }
        } else {

        }
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        init();
        loadUserDetails();
        getToken();
        setListeners();
        listenConversations();
    }

    // Thiết lập danh sách cuộc trò chuyện, adapter và Firestore.
    private void init() {
        conversations = new ArrayList<>();
        conversationAdapter = new RecentConversationAdapter(conversations, this, preferenceManager);
        binding.conversationRecyclerView.setAdapter(conversationAdapter);
        database = FirebaseFirestore.getInstance();
    }

    private void setListeners() {
        binding.SignOutImageView.setOnClickListener(v -> signOut());
        // Chuyển đến UsersActivity để bắt đầu cuộc trò chuyện mới (tìm kiếm).
        binding.fabNewChat.setOnClickListener(v ->
                startActivity(new Intent(getApplicationContext(), UsersActivity.class)));
        // Chuyển đến ProfileUser để xem thông tin hồ sơ.
        binding.ProfileImage.setOnClickListener(v ->
                startActivity(new Intent(getApplicationContext(), ProfileUser.class)));
//        binding.chatbot.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, BotActivity.class)));
    }

    // Hiển thị tên và ảnh hồ sơ người dùng.
    private void loadUserDetails() {
        // Lấy tên từ KEY_NAME và đặt vào binding.textName
        binding.textName.setText(preferenceManager.getString(Constants.KEY_NAME));
        // Giải mã ảnh hồ sơ từ KEY_IMAGE (chuỗi Base64) thành Bitmap và hiển thị trong binding.ProfileImage.
        byte[] bytes = android.util.Base64.decode(preferenceManager.getString(Constants.KEY_IMAGE), android.util.Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        binding.ProfileImage.setImageBitmap(bitmap);
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    // Lắng nghe thay đổi trong cuộc trò chuyện từ Firestore
    private void listenConversations() {
        // Lắng nghe thay đổi trong collection KEY_CONVERSATION_ID của Firestore:
        // Truy vấn các cuộc trò chuyện nơi người dùng là KEY_SENDER_ID hoặc KEY_RECEIVER_ID.
        // Sử dụng addSnapshotListener để cập nhật giao diện theo thời gian thực.
        database.collection(Constants.KEY_CONVERSATION_ID)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
        database.collection(Constants.KEY_CONVERSATION_ID)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

    @SuppressLint("NotifyDataSetChanged")
    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if (error != null) {
            return;
        }
        if (value != null) {
            for (DocumentChange documentChange : value.getDocumentChanges()) {
                // Thêm mới (khi người mới nhắn cho mình)
                // Xác định thông tin đối phương (conversionName, conversionImage, conversionId) dựa trên senderId hoặc receiverId.
                // Lưu tin nhắn cuối (KEY_LAST_MESSAGE) và thời gian (KEY_TIMESTAMP).
                // Thêm vào danh sách conversations.
                if (documentChange.getType() == DocumentChange.Type.ADDED) {
                    String senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                    String receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.senderId = senderId;
                    chatMessage.receiverId = receiverId;
                    if (preferenceManager.getString(Constants.KEY_USER_ID).equals(senderId)) {
                        chatMessage.conversionImage = documentChange.getDocument().getString(Constants.KEY_RECEIVER_IMAGE);
                        chatMessage.conversionName = documentChange.getDocument().getString(Constants.KEY_RECEIVER_NAME);
                        chatMessage.conversionId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                    } else {
                        chatMessage.conversionImage = documentChange.getDocument().getString(Constants.KEY_SENDER_IMAGE);
                        chatMessage.conversionName = documentChange.getDocument().getString(Constants.KEY_SENDER_NAME);
                        chatMessage.conversionId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                    }
                    chatMessage.message = documentChange.getDocument().getString(Constants.KEY_LAST_MESSAGE);
                    chatMessage.dateObject = getDateObject(documentChange.getDocument().getLong(Constants.KEY_TIMESTAMP));
                    conversations.add(chatMessage);
                }

                // Sửa đổi (khi người cũ nhắn tin nhắn mới)
                // Cập nhật tin nhắn cuối và thời gian cho cuộc trò chuyện tương ứng.
                else if (documentChange.getType() == DocumentChange.Type.MODIFIED) {
                    for (int i = 0; i < conversations.size(); i++) {
                        String senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                        String receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                        if (conversations.get(i).senderId.equals(senderId) && conversations.get(i).receiverId.equals(receiverId)) {
                            conversations.get(i).message = documentChange.getDocument().getString(Constants.KEY_LAST_MESSAGE);
                            conversations.get(i).dateObject = getDateObject(documentChange.getDocument().getLong(Constants.KEY_TIMESTAMP));
                            break;
                        }
                    }
                }
            }

            // Sắp xếp danh sách theo thời gian giảm dần (dateObject).
            //Cập nhật RecyclerView bằng notifyDataSetChanged và cuộn đến vị trí đầu tiên.
            conversations.sort((obj1, obj2) -> obj2.dateObject.compareTo(obj1.dateObject));
            conversationAdapter.notifyDataSetChanged();
            binding.conversationRecyclerView.smoothScrollToPosition(0);
            binding.conversationRecyclerView.setVisibility(View.VISIBLE);
            binding.progressBar.setVisibility(View.GONE);
        }
    };

    private Date getDateObject(Long timestamp) {
        return new Date(timestamp);
    }

    private void getToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(this::updateToken)
                .addOnFailureListener(e -> showToast("Unable to get token: " + e.getMessage()));
    }

    private void updateToken(String token) {
        preferenceManager.putString(Constants.KEY_FCM_TOKEN, token);
        Log.d("FCM", "Token: " + token);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference documentReference = database.collection(Constants.KEY_COLLECTION_USERS).document(
                preferenceManager.getString(Constants.KEY_USER_ID)
        );

        documentReference.update(Constants.KEY_FCM_TOKEN, token)
                .addOnFailureListener(e -> showToast("Unable to send token: " + e.getMessage()));
    }

    private void signOut() {
        showToast("Signing out...");
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference documentReference = database.collection(Constants.KEY_COLLECTION_USERS).document(
                preferenceManager.getString(Constants.KEY_USER_ID)
        );
        HashMap<String, Object> updates = new HashMap<>();
        updates.put(Constants.KEY_FCM_TOKEN, FieldValue.delete());
        documentReference.update(updates)
                .addOnSuccessListener(unused -> {
                    // Xóa dữ liệu trong PreferenceManager bằng clearPreferences.
                    preferenceManager.clearPreferences();
                    startActivity(new Intent(getApplicationContext(), SignInActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> showToast("Unable to sign out"));
    }


    @Override
    // Khi người dùng nhấp vào một cuộc trò chuyện, chuyển đến ChatActivity và truyền thông tin người dùng (User) qua Intent.
    public void onConversionClicked(User user) {
        Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
        intent.putExtra(Constants.KEY_USER, user);
        startActivity(intent);
    }
}