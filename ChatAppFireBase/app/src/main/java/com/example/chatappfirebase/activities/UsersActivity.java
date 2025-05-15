package com.example.chatappfirebase.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.example.chatappfirebase.R;
import com.example.chatappfirebase.activities.qrcode.ScanQrActivity;
import com.example.chatappfirebase.adapters.UsersAdapter;
import com.example.chatappfirebase.databinding.ActivityUsersBinding;
import com.example.chatappfirebase.listeners.UserListener;
import com.example.chatappfirebase.models.User;
import com.example.chatappfirebase.utilities.Constants;
import com.example.chatappfirebase.utilities.PreferenceManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


// Màn hình hiển thị danh sách người dùng trong ứng dụng chat, cho phép người dùng tìm kiếm và chọn người để bắt đầu cuộc
// trò chuyện. File cũng cung cấp chức năng quét mã QR (chuyển đến ScanQrActivity) để thêm bạn bè.
// Triển khai interface UserListener để xử lý sự kiện khi nhấp vào một người dùng.
public class UsersActivity extends BaseActivity implements UserListener {

    private ActivityUsersBinding binding;

    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityUsersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        setListeners();
    }

    private void setListeners() {
        binding.backImageView.setOnClickListener(v -> onBackPressed());
        binding.SeachEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                getUsers();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                getUsers();
            }

            @Override
            public void afterTextChanged(Editable s) {
                getUsers();
            }
        });
        binding.scanButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                Intent intent = new Intent(UsersActivity.this, ScanQrActivity.class);
                startActivity(intent);
                finish();


            }
        });
    }

    // Lấy danh sách người dùng từ Firestore và hiển thị trong RecyclerView
    private void getUsers() {
        loading(true);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        database.collection(Constants.KEY_COLLECTION_USERS)
                .get()
                .addOnCompleteListener(task -> {
                    loading(false);

                    // Truy vấn collection KEY_COLLECTION_USERS để lấy tất cả người dùng.
                    // Lọc người dùng hiện tại (currentUserId) ra khỏi danh sách.
                    // Lọc theo tên dựa trên văn bản tìm kiếm (searchText) từ SeachEditText.
                    String currentUserId = preferenceManager.getString(Constants.KEY_USER_ID);
                    String searchText = Objects.requireNonNull(binding.SeachEditText).getText().toString().trim().toLowerCase();
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<User> users = new ArrayList<>();

                        // Tạo danh sách users chứa các đối tượng User với thông tin name, email, image, token, và id.
                        for (QueryDocumentSnapshot queryDocumentSnapshot : task.getResult()) {
                            if (currentUserId.equals(queryDocumentSnapshot.getId())) {
                                continue;
                            }
                            User user = new User();
                            user.name = queryDocumentSnapshot.getString(Constants.KEY_NAME);
                            user.email = queryDocumentSnapshot.getString(Constants.KEY_EMAIL);
                            user.image = queryDocumentSnapshot.getString(Constants.KEY_IMAGE);
                            user.token = queryDocumentSnapshot.getString(Constants.KEY_FCM_TOKEN);
                            user.id = queryDocumentSnapshot.getId();
                            if (user.name != null && user.name.toLowerCase().contains(searchText.toLowerCase())) {
                                users.add(user);
                            }
                        }

                        // Nếu tìm thấy người dùng khớp với searchText, tạo UsersAdapter và gắn vào usersRecyclerView.
                        if (users.size() > 0) {
                            UsersAdapter usersAdapter = new UsersAdapter(users, this);
                            binding.usersRecyclerView.setAdapter(usersAdapter);
                            binding.usersRecyclerView.setVisibility(View.VISIBLE);
                        } else {
                            showErrorMessage();
                        }
                    } else {
                        showErrorMessage();
                    }
                });
    }

    private void showErrorMessage() {

    }

    private void loading(boolean isLoading) {
        if (isLoading) {
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    // Khi người dùng nhấp vào một người trong danh sách, tạo Intent để chuyển đến ChatActivity, truyền thông tin
    // người dùng (User) qua KEY_USER.
    public void onUserClicked(User user) {
        Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
        intent.putExtra(Constants.KEY_USER, user);
        startActivity(intent);
        finish();
    }
}