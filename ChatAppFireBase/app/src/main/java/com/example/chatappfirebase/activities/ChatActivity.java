package com.example.chatappfirebase.activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.example.chatappfirebase.R;
import com.example.chatappfirebase.adapters.ChatAdapter;
import com.example.chatappfirebase.cloudinary.Config;
import com.example.chatappfirebase.databinding.ActivityChatBinding;
import com.example.chatappfirebase.models.ChatMessage;
import com.example.chatappfirebase.models.User;
import com.example.chatappfirebase.network.ApiClient;
import com.example.chatappfirebase.network.ApiService;
import com.example.chatappfirebase.utilities.Constants;
import com.example.chatappfirebase.utilities.PreferenceManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.makeramen.roundedimageview.RoundedImageView;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Đây là màn hình chính để người dùng trò chuyện với một người dùng khác (người nhận, receiverUser). File hỗ trợ gửi tin
// nhắn văn bản, gửi ảnh, chuyển đổi ảnh thành phong cách anime bằng mô hình TensorFlow Lite, và hiển thị thông tin hồ
// sơ bạn bè. Sử dụng Firebase Firestore để lưu trữ và lắng nghe tin nhắn, Firebase Storage/Cloudinary để tải ảnh.
public class ChatActivity extends BaseActivity {

    private ActivityChatBinding binding;
    // Thông tin người nhận (tên, email, ảnh, ID)
    private User receiverUser;

    // Danh sách tin nhắn trong cuộc trò chuyện.
    private List<ChatMessage> chatMessages;

    // Adapter để hiển thị tin nhắn trong RecyclerView
    private ChatAdapter chatAdapter;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore database;

    // ID của cuộc trò chuyện trong Firestore
    private String conversionId = null;
    private final OnCompleteListener<QuerySnapshot> completeListener = task -> {
        if (task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0) {
            DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
            conversionId = documentSnapshot.getId();
        }
    };
    @SuppressLint("NotifyDataSetChanged")
    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if (error != null) {
            return;
        }
        if (value != null) {
            int count = chatMessages.size();
            for (DocumentChange documentChange : value.getDocumentChanges()) {
                if (documentChange.getType() == DocumentChange.Type.ADDED) {
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                    chatMessage.receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                    chatMessage.message = documentChange.getDocument().getString(Constants.KEY_MESSAGE);
                    chatMessage.dateTime = getReadableDateTime(documentChange.getDocument().getLong(Constants.KEY_TIMESTAMP));
                    chatMessage.dateObject = getDateObject(documentChange.getDocument().getLong(Constants.KEY_TIMESTAMP));

                    // Tìm và thay thế tin nhắn tạm (nếu có)
                    boolean isReplaced = false;
                    for (int i = 0; i < chatMessages.size(); i++) {
                        ChatMessage existingMessage = chatMessages.get(i);
                        // Điều kiện: Cùng sender, receiver và là tin nhắn tạm
                        if (existingMessage.message.equals("temp_image_placeholder") &&
                                existingMessage.senderId.equals(chatMessage.senderId) &&
                                existingMessage.receiverId.equals(chatMessage.receiverId)) {
                            // Cập nhật thông tin từ Firestore vào tin nhắn tạm
                            existingMessage.message = chatMessage.message;
                            existingMessage.dateTime = chatMessage.dateTime;
                            existingMessage.dateObject = chatMessage.dateObject;
                            // Thông báo Adapter cập nhật item tại vị trí i
                            chatAdapter.notifyItemChanged(i);
                            isReplaced = true;
                            break; // Thoát vòng lặp sau khi thay thế
                        }
                    }

                    // Nếu không thay thế được (tin nhắn mới từ người khác), thêm vào danh sách
                    if (!isReplaced) {
                        chatMessages.add(chatMessage);
                    }
                }
            }
            chatMessages.sort(Comparator.comparing(obj -> obj.dateObject));
            if (count == 0) {
                chatAdapter.notifyDataSetChanged();
            } else {
                chatAdapter.notifyItemRangeInserted(chatMessages.size(), chatMessages.size());
                binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
            }
            binding.chatRecyclerView.setVisibility(View.VISIBLE);
        }
        binding.progressBar.setVisibility(View.GONE);
        if (conversionId == null) {
            checkForConversion();
        }
    };
    // Trạng thái trực tuyến của người nhận
    private Boolean isReceiverAvailable = false;
    private FirebaseStorage storage;
    private StorageReference storageReference;
    private Uri imageURI;
    private Uri animeImageUri;
    private String imageUrl;
    private ImageView image_send;
    private ImageView img_anime;
    private AppCompatImageView profile;
    private Interpreter tflite; // TensorFlow Lite Interpreter
    private ExecutorService executorService; // Thread pool để chạy suy luận
    private Bitmap animeBitmap; // Lưu ảnh anime tạm thời sau chuyển đổi

    // New constants for camera
    private static final int REQUEST_CAMERA = 30;
    private static final int REQUEST_CAMERA_PERMISSION = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setNavigationBarColor(getResources().getColor(R.color.background));

        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        storage = FirebaseStorage.getInstance();
        storageReference = storage.getReference();
        profile = findViewById(R.id.infoImageView);

        image_send = findViewById(R.id.img_send);
        img_anime = findViewById(R.id.img_anime); // Khởi tạo img_anime (img_new)

        // Khởi tạo mô hình TensorFlow Lite
        try {
            tflite = new Interpreter(loadModelFile());
        } catch (IOException e) {
            e.printStackTrace();
            showToast("Không thể tải mô hình Selfie2Anime");
        }

        // Khởi tạo thread pool
        executorService = Executors.newSingleThreadExecutor();

        loadReceiverDetails();
        setListeners();
        init();
        listenMessages();
    }

    // Hàm tải mô hình từ ml
    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("selfie2anime.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void init() {
        database = FirebaseFirestore.getInstance();

        preferenceManager = new PreferenceManager(getApplicationContext());
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(
                chatMessages,
                getBitmapFromEncodedString(receiverUser.image),
                preferenceManager.getString(Constants.KEY_USER_ID)
        );
        binding.chatRecyclerView.setAdapter(chatAdapter);
    }

    private void sendImg() {
        // Thêm tin nhắn tạm
        ChatMessage tempMessage = new ChatMessage();
        tempMessage.senderId = preferenceManager.getString(Constants.KEY_USER_ID);
        tempMessage.receiverId = receiverUser.id;
        tempMessage.message = "temp_image_placeholder";
        tempMessage.localImageUri = imageURI.toString(); // Lưu URI ảnh đã chọn
        tempMessage.dateTime = "Đang gửi...";
        tempMessage.dateObject = new Date();

        chatMessages.add(tempMessage);
        int position = chatMessages.size() - 1;
        chatAdapter.notifyItemInserted(position);
        binding.chatRecyclerView.smoothScrollToPosition(position);

        String uploadPreset = Config.getUploadPreset();
        MediaManager.get().upload(imageURI)
                .unsigned(uploadPreset)
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                        // Tùy chọn: Hiển thị tiến trình tải lên
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                        // Tùy chọn: Cập nhật tiến trình tải lên
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String imageUrl = (String) resultData.get("secure_url");
                        HashMap<String, Object> messageMap = new HashMap<>();
                        messageMap.put(Constants.KEY_SENDER_ID, tempMessage.senderId);
                        messageMap.put(Constants.KEY_RECEIVER_ID, tempMessage.receiverId);
                        messageMap.put(Constants.KEY_MESSAGE, imageUrl);
                        messageMap.put(Constants.KEY_TIMESTAMP, System.currentTimeMillis());
                        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).add(messageMap);
                        if (conversionId != null) {
                            updateConversion("Hình ảnh.");
                        } else {
                            HashMap<String, Object> conversion = new HashMap<>();
                            conversion.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
                            conversion.put(Constants.KEY_SENDER_NAME, preferenceManager.getString(Constants.KEY_NAME));
                            conversion.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
                            conversion.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
                            conversion.put(Constants.KEY_RECEIVER_NAME, receiverUser.name);
                            conversion.put(Constants.KEY_RECEIVER_IMAGE, receiverUser.image);
                            conversion.put(Constants.KEY_LAST_MESSAGE, "Hình ảnh.");
                            conversion.put(Constants.KEY_TIMESTAMP, System.currentTimeMillis());
                            addConversion(conversion);
                        }
                        if (!isReceiverAvailable) {
                            try {
                                JSONArray tokens = new JSONArray();
                                tokens.put(receiverUser.token);

                                JSONObject data = new JSONObject();
                                data.put(Constants.KEY_USER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
                                data.put(Constants.KEY_NAME, preferenceManager.getString(Constants.KEY_NAME));
                                data.put(Constants.KEY_FCM_TOKEN, preferenceManager.getString(Constants.KEY_FCM_TOKEN));
                                data.put(Constants.KEY_MESSAGE, "Hình ảnh.");

                                JSONObject body = new JSONObject();
                                body.put(Constants.REMOTE_MSG_DATA, data);
                                body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

                                sendNotification(body.toString());
                            } catch (Exception exception) {
                                // Xử lý ngoại lệ
                            }
                        }
                        binding.messageEditText.setText(null);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        // Xử lý lỗi
                        showToast("Tải lên thất bại: " + error.getDescription());
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                        // Xử lý lên lịch lại
                    }
                })
                .dispatch();
    }

    // Hàm gửi ảnh anime
    private void sendAnimeImg() {
        if (animeBitmap == null) {
            showToast("Không có ảnh anime để gửi");
            return;
        }

        // Lưu ảnh anime vào bộ nhớ tạm và lấy URI
        Uri animeUri = saveAnimeBitmapToCache(animeBitmap);

        // Thêm tin nhắn tạm
        ChatMessage tempMessage = new ChatMessage();
        tempMessage.senderId = preferenceManager.getString(Constants.KEY_USER_ID);
        tempMessage.receiverId = receiverUser.id;
        tempMessage.message = "temp_image_placeholder";
        tempMessage.localImageUri = animeUri.toString();
        tempMessage.dateTime = "Đang gửi...";
        tempMessage.dateObject = new Date();

        chatMessages.add(tempMessage);
        int position = chatMessages.size() - 1;
        chatAdapter.notifyItemInserted(position);
        binding.chatRecyclerView.smoothScrollToPosition(position);

        // Chuyển Bitmap thành byte array để upload
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        animeBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] imageData = baos.toByteArray();

        String uploadPreset = Config.getUploadPreset();
        MediaManager.get().upload(imageData)
                .unsigned(uploadPreset)
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                        // Tùy chọn: Hiển thị tiến trình tải lên
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                        // Tùy chọn: Cập nhật tiến trình tải lên
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String imageUrl = (String) resultData.get("secure_url");
                        HashMap<String, Object> messageMap = new HashMap<>();
                        messageMap.put(Constants.KEY_SENDER_ID, tempMessage.senderId);
                        messageMap.put(Constants.KEY_RECEIVER_ID, tempMessage.receiverId);
                        messageMap.put(Constants.KEY_MESSAGE, imageUrl);
                        messageMap.put(Constants.KEY_TIMESTAMP, System.currentTimeMillis());
                        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).add(messageMap);
                        if (conversionId != null) {
                            updateConversion("Hình ảnh anime.");
                        } else {
                            HashMap<String, Object> conversion = new HashMap<>();
                            conversion.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
                            conversion.put(Constants.KEY_SENDER_NAME, preferenceManager.getString(Constants.KEY_NAME));
                            conversion.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
                            conversion.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
                            conversion.put(Constants.KEY_RECEIVER_NAME, receiverUser.name);
                            conversion.put(Constants.KEY_RECEIVER_IMAGE, receiverUser.image);
                            conversion.put(Constants.KEY_LAST_MESSAGE, "Hình ảnh anime.");
                            conversion.put(Constants.KEY_TIMESTAMP, System.currentTimeMillis());
                            addConversion(conversion);
                        }
                        if (!isReceiverAvailable) {
                            try {
                                JSONArray tokens = new JSONArray();
                                tokens.put(receiverUser.token);

                                JSONObject data = new JSONObject();
                                data.put(Constants.KEY_USER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
                                data.put(Constants.KEY_NAME, preferenceManager.getString(Constants.KEY_NAME));
                                data.put(Constants.KEY_FCM_TOKEN, preferenceManager.getString(Constants.KEY_FCM_TOKEN));
                                data.put(Constants.KEY_MESSAGE, "Hình ảnh anime.");

                                JSONObject body = new JSONObject();
                                body.put(Constants.REMOTE_MSG_DATA, data);
                                body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

                                sendNotification(body.toString());
                            } catch (Exception exception) {
                                // Xử lý ngoại lệ
                            }
                        }
                        binding.messageEditText.setText(null);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        showToast("Tải lên thất bại: " + error.getDescription());
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                        // Xử lý lên lịch lại
                    }
                })
                .dispatch();
    }

    // Hàm lưu ảnh anime vào cache và trả về URI
    private Uri saveAnimeBitmapToCache(Bitmap bitmap) {
        File cacheDir = getCacheDir();
        File imageFile = new File(cacheDir, "temp_anime_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Uri.fromFile(imageFile);
    }

    private void sendMessage() {
        if (binding.messageEditText.getText().toString().trim().isEmpty()) {
            return;
        }
        HashMap<String, Object> message = new HashMap<>();
        message.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        message.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
        message.put(Constants.KEY_MESSAGE, binding.messageEditText.getText().toString().trim());
        message.put(Constants.KEY_TIMESTAMP, System.currentTimeMillis());
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).add(message);
        if (conversionId != null) {
            updateConversion(binding.messageEditText.getText().toString().trim());
        } else {
            HashMap<String, Object> conversion = new HashMap<>();
            conversion.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
            conversion.put(Constants.KEY_SENDER_NAME, preferenceManager.getString(Constants.KEY_NAME));
            conversion.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
            conversion.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
            conversion.put(Constants.KEY_RECEIVER_NAME, receiverUser.name);
            conversion.put(Constants.KEY_RECEIVER_IMAGE, receiverUser.image);
            conversion.put(Constants.KEY_LAST_MESSAGE, binding.messageEditText.getText().toString().trim());
            conversion.put(Constants.KEY_TIMESTAMP, System.currentTimeMillis());
            addConversion(conversion);
        }
        if (!isReceiverAvailable) {
            try {
                JSONArray tokens = new JSONArray();
                tokens.put(receiverUser.token);

                JSONObject data = new JSONObject();
                data.put(Constants.KEY_USER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
                data.put(Constants.KEY_NAME, preferenceManager.getString(Constants.KEY_NAME));
                data.put(Constants.KEY_FCM_TOKEN, preferenceManager.getString(Constants.KEY_FCM_TOKEN));
                data.put(Constants.KEY_MESSAGE, binding.messageEditText.getText().toString().trim());

                JSONObject body = new JSONObject();
                body.put(Constants.REMOTE_MSG_DATA, data);
                body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

                sendNotification(body.toString());
            } catch (Exception exception) {

            }
        }
        binding.messageEditText.setText(null);
    }

    private void sendNotification(String messageBody) {
        ApiClient.getClient().create(ApiService.class).sendMessage(
                Constants.getRemoteMsgHeaders(),
                messageBody
        ).enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if (response.isSuccessful()) {
                    try {
                        if (response.body() != null) {
                            JSONObject responseJson = new JSONObject(response.body());
                            JSONArray results = responseJson.getJSONArray("results");
                            if (responseJson.getInt("failure") == 1) {
                                JSONObject error = (JSONObject) results.get(0);

                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
            }
        });
    }

    private void listenAvailabilityOfReceiver() {
        database.collection(Constants.KEY_COLLECTION_USERS)
                .document(receiverUser.id)
                .addSnapshotListener(ChatActivity.this, (value, error) -> {
                    if (error != null) {
                        return;
                    }
                    if (value != null) {
                        if (value.getLong(Constants.KEY_AVAILABILITY) != null) {
                            int availability = Objects.requireNonNull(value.getLong(Constants.KEY_AVAILABILITY)).intValue();
                            isReceiverAvailable = availability == 1;
                        }
                        receiverUser.email = value.getString(Constants.KEY_EMAIL);
                        receiverUser.token = value.getString(Constants.KEY_FCM_TOKEN);
                        if (receiverUser.image == null) {
                            receiverUser.image = value.getString(Constants.KEY_IMAGE);
                            chatAdapter.setReceiverProfileImage(getBitmapFromEncodedString(receiverUser.image));
                            chatAdapter.notifyItemRangeChanged(0, chatMessages.size());
                        }
                    }
                    if (isReceiverAvailable) {
                        binding.textAvailability.setText("Online");
                        binding.infoImageView.setColorFilter(Color.parseColor("#FF19AE6D"));
                    } else {
                        binding.textAvailability.setText("Offline");
                        binding.infoImageView.setColorFilter(Color.parseColor("#FFEEA5A5"));
                    }
                });
    }

    private void listenMessages() {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.id)
                .addSnapshotListener(eventListener);
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

    private Bitmap getBitmapFromEncodedString(String encodedImage) {
        if (encodedImage != null) {
            byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } else {
            return null;
        }
    }

    private void loadReceiverDetails() {
        receiverUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);
        assert receiverUser != null;
        binding.textName.setText(receiverUser.name);
    }

    private void setListeners() {
        image_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select Picture"), 10);

            }
        });
        // Listener cho img_anime (img_new)
        img_anime.setOnClickListener(v -> showAnimeOptionsDialog());
        profile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                profileFriend();
            }
        });
        binding.backImageView.setOnClickListener(v -> onBackPressed());
        binding.sendLayout.setOnClickListener(v -> sendMessage());
    }

    // New method to show dialog with selfie or gallery options
    private void showAnimeOptionsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chọn phương thức");
        builder.setItems(new CharSequence[]{"Chụp ảnh selfie", "Chọn từ thư viện"}, (dialog, which) -> {
            if (which == 0) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    openCamera();
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                }
            } else {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select Selfie for Anime"), 20);
            }
        });
        builder.show();
    }

    // New method to open camera
    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_CAMERA);
        }
    }

    private String getReadableDateTime(Long timestamp) {
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault());
            return simpleDateFormat.format(new Date(timestamp));
        } catch (Exception e) {
            return new Date(timestamp).toString();
        }
    }

    private Date getDateObject(Long timestamp) {
        return new Date(timestamp);
    }

    private void checkForConversion() {
        if (chatMessages.size() != 0) {
            checkForConversionRemotely(
                    preferenceManager.getString(Constants.KEY_USER_ID),
                    receiverUser.id
            );
            checkForConversionRemotely(
                    receiverUser.id,
                    preferenceManager.getString(Constants.KEY_USER_ID)
            );
        }
    }

    private void addConversion(HashMap<String, Object> conversion) {
        database.collection(Constants.KEY_CONVERSATION_ID)
                .add(conversion)
                .addOnSuccessListener(documentReference -> conversionId = documentReference.getId());
    }

    private void updateConversion(String message) {
        DocumentReference documentReference = database.collection(Constants.KEY_CONVERSATION_ID).document(conversionId);
        documentReference.update(
                Constants.KEY_LAST_MESSAGE, message,
                Constants.KEY_TIMESTAMP, System.currentTimeMillis()
        );
    }

    private void checkForConversionRemotely(String senderId, String receiverId) {
        database.collection(Constants.KEY_CONVERSATION_ID)
                .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(completeListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        listenAvailabilityOfReceiver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tflite != null) {
            tflite.close();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                showToast("Quyền camera bị từ chối. Không thể chụp ảnh selfie.");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 10 && resultCode == RESULT_OK && data != null) {
            imageURI = data.getData();
            image_send.setImageURI(imageURI);
            AlertDialog.Builder builder = new AlertDialog.Builder(ChatActivity.this);
            LayoutInflater inflater = LayoutInflater.from(ChatActivity.this);

            // Inflate the layout for the dialog
            View dialogView = inflater.inflate(R.layout.image_dialog_layout, null);
            builder.setView(dialogView);

            // Find the ImageView in the layout
            ImageView imageView = dialogView.findViewById(R.id.imageView);
            ImageView cancel = dialogView.findViewById(R.id.buttonCancel);
            ImageView buttonSend = dialogView.findViewById(R.id.buttonSend);
            ProgressBar progressBar = dialogView.findViewById(R.id.progressBar);
            TextView loadingText = dialogView.findViewById(R.id.loadingText);

            // Ẩn ProgressBar và loadingText cho ảnh thông thường
            progressBar.setVisibility(View.GONE);
            loadingText.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
            cancel.setVisibility(View.VISIBLE);
            buttonSend.setVisibility(View.VISIBLE);

            // Use Glide to load and display the image into the ImageView
            Picasso.get()
                    .load(imageURI.toString())
                    .into(imageView);

            // Create and show the dialog
            AlertDialog dialog = builder.create();
            dialog.show();
            buttonSend.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendImg();
                    ImageView imageView = findViewById(R.id.img_send);
                    Picasso.get().load(R.drawable.send_image).into(imageView);
//                        Picasso.get().load("https://firebasestorage.googleapis.com/v0/b/btl-java-04.appspot.com/o/images%2Fsend_image.png?alt=media&token=75b57268-728e-4c15-a616-8ad354ff5fb4").into(imageView);
                    dialog.dismiss();
                }
            });
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }

        // Xử lý cho img_anime (chuyển ảnh thành anime)
        // Handle gallery selection
        if (requestCode == 20 && resultCode == RESULT_OK && data != null) {
            imageURI = data.getData();
            processAnimeConversion(imageURI);
        }

        // Handle camera capture
        if (requestCode == REQUEST_CAMERA && resultCode == RESULT_OK && data != null) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            showConfirmationDialog(imageBitmap);
        }
    }

    // New method to show confirmation dialog after selfie
    private void showConfirmationDialog(Bitmap bitmap) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.image_dialog_layout, null);
        builder.setView(dialogView);

        ImageView imageView = dialogView.findViewById(R.id.imageView);
        ImageView cancel = dialogView.findViewById(R.id.buttonCancel);
        ImageView buttonSend = dialogView.findViewById(R.id.buttonSend);
        ProgressBar progressBar = dialogView.findViewById(R.id.progressBar);
        TextView loadingText = dialogView.findViewById(R.id.loadingText);

        progressBar.setVisibility(View.GONE);
        loadingText.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);
        cancel.setVisibility(View.VISIBLE);
        buttonSend.setVisibility(View.VISIBLE);
        buttonSend.setImageResource(android.R.drawable.ic_menu_set_as); // Use "Hoàn thành" icon
        imageView.setImageBitmap(bitmap);

        AlertDialog dialog = builder.create();
        dialog.show();

        buttonSend.setOnClickListener(v -> {
            dialog.dismiss();
            processAnimeConversion(bitmap);
        });
        cancel.setOnClickListener(v -> dialog.dismiss());
    }

    // New method to process anime conversion (overloaded for Uri and Bitmap)
    private void processAnimeConversion(Uri imageUri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.image_dialog_layout, null);
        builder.setView(dialogView);

        ImageView imageView = dialogView.findViewById(R.id.imageView);
        ImageView cancel = dialogView.findViewById(R.id.buttonCancel);
        ImageView buttonSend = dialogView.findViewById(R.id.buttonSend);
        ImageView buttonSave = dialogView.findViewById(R.id.buttonSave);
        ProgressBar progressBar = dialogView.findViewById(R.id.progressBar);
        TextView loadingText = dialogView.findViewById(R.id.loadingText);

        progressBar.setVisibility(View.VISIBLE);
        loadingText.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);
        cancel.setVisibility(View.GONE);
        buttonSend.setVisibility(View.GONE);
        buttonSave.setVisibility(View.GONE);

        AlertDialog dialog = builder.create();
        dialog.show();

        executorService.execute(() -> {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                processAnimeConversion(bitmap, dialog, imageView, cancel, buttonSend, buttonSave, progressBar, loadingText);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showToast("Lỗi khi chuyển đổi ảnh sang anime: " + e.getMessage());
                    dialog.dismiss();
                });
            }
        });
    }

    private void processAnimeConversion(Bitmap bitmap) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.image_dialog_layout, null);
        builder.setView(dialogView);

        ImageView imageView = dialogView.findViewById(R.id.imageView);
        ImageView cancel = dialogView.findViewById(R.id.buttonCancel);
        ImageView buttonSend = dialogView.findViewById(R.id.buttonSend);
        ImageView buttonSave = dialogView.findViewById(R.id.buttonSave);
        ProgressBar progressBar = dialogView.findViewById(R.id.progressBar);
        TextView loadingText = dialogView.findViewById(R.id.loadingText);

        progressBar.setVisibility(View.VISIBLE);
        loadingText.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);
        cancel.setVisibility(View.GONE);
        buttonSend.setVisibility(View.GONE);
        buttonSave.setVisibility(View.GONE);

        AlertDialog dialog = builder.create();
        dialog.show();

        executorService.execute(() -> processAnimeConversion(bitmap, dialog, imageView, cancel, buttonSend, buttonSave, progressBar, loadingText));
    }

    private void processAnimeConversion(Bitmap bitmap, AlertDialog dialog, ImageView imageView, ImageView cancel, ImageView buttonSend, ImageView buttonSave, ProgressBar progressBar, TextView loadingText) {
        try {
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, true);
            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(1 * 256 * 256 * 3 * 4);
            inputBuffer.order(ByteOrder.nativeOrder());

            for (int y = 0; y < 256; y++) {
                for (int x = 0; x < 256; x++) {
                    int pixel = resizedBitmap.getPixel(x, y);
                    float r = ((pixel >> 16) & 0xFF) / 127.5f - 1.0f;
                    float g = ((pixel >> 8) & 0xFF) / 127.5f - 1.0f;
                    float b = (pixel & 0xFF) / 127.5f - 1.0f;
                    inputBuffer.putFloat(r);
                    inputBuffer.putFloat(g);
                    inputBuffer.putFloat(b);
                }
            }

            ByteBuffer outputBuffer = ByteBuffer.allocateDirect(1 * 256 * 256 * 3 * 4);
            outputBuffer.order(ByteOrder.nativeOrder());

            Object[] inputs = {inputBuffer};
            Map<Integer, Object> outputs = new HashMap<>();
            outputs.put(0, outputBuffer);
            tflite.runForMultipleInputsOutputs(inputs, outputs);

            animeBitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
            outputBuffer.rewind();
            for (int y = 0; y < 256; y++) {
                for (int x = 0; x < 256; x++) {
                    float r = outputBuffer.getFloat();
                    float g = outputBuffer.getFloat();
                    float b = outputBuffer.getFloat();
                    int red = (int) ((r + 1.0f) * 127.5f);
                    int green = (int) ((g + 1.0f) * 127.5f);
                    int blue = (int) ((b + 1.0f) * 127.5f);
                    red = Math.max(0, Math.min(255, red));
                    green = Math.max(0, Math.min(255, green));
                    blue = Math.max(0, Math.min(255, blue));
                    int pixel = 0xFF000000 | (red << 16) | (green << 8) | blue;
                    animeBitmap.setPixel(x, y, pixel);
                }
            }

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                loadingText.setVisibility(View.GONE);
                imageView.setVisibility(View.VISIBLE);
                cancel.setVisibility(View.VISIBLE);
                buttonSend.setVisibility(View.VISIBLE);
                buttonSave.setVisibility(View.VISIBLE);
                imageView.setImageBitmap(animeBitmap);

                buttonSend.setOnClickListener(v -> {
                    sendAnimeImg();
                    dialog.dismiss();
                });
                buttonSave.setOnClickListener(v -> {
                    try {
                        MediaStore.Images.Media.insertImage(getContentResolver(), animeBitmap, "AnimeImage_" + System.currentTimeMillis(), "Anime Image");
                        showToast("Đã lưu ảnh anime vào thư viện");
                    } catch (Exception e) {
                        showToast("Lưu ảnh thất bại: " + e.getMessage());
                    }
                });
                cancel.setOnClickListener(v -> dialog.dismiss());
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                showToast("Lỗi khi chuyển đổi ảnh sang anime: " + e.getMessage());
                dialog.dismiss();
            });
        }
    }

    private void profileFriend() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.setContentView(R.layout.info_dialog_layout);

        // the layout
        TextView email = dialog.findViewById(R.id.emailProfileFriend);
        RoundedImageView imageProfile = dialog.findViewById(R.id.profileImageFriend);
        TextView name = dialog.findViewById(R.id.nameFriend);

        email.setText(receiverUser.email);
        name.setText(receiverUser.name);
        byte[] bytes = android.util.Base64.decode(receiverUser.image, android.util.Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        imageProfile.setImageBitmap(bitmap);

        // Create and show the dialog
        dialog.show();
    }
}