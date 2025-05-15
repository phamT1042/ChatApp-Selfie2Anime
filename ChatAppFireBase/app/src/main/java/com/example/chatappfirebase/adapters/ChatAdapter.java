package com.example.chatappfirebase.adapters;


import android.graphics.Bitmap;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappfirebase.R;
import com.example.chatappfirebase.databinding.ItemContainerReceivedMessageBinding;
import com.example.chatappfirebase.databinding.ItemContainerSentMessageBinding;
import com.example.chatappfirebase.models.ChatMessage;
import com.makeramen.roundedimageview.RoundedImageView;
import com.squareup.picasso.Picasso;

import java.util.List;

//Đây là một RecyclerView.Adapter dùng để hiển thị danh sách tin nhắn trong giao diện trò chuyện của ChatActivity.
// Adapter hỗ trợ bốn loại tin nhắn:
//Tin nhắn văn bản gửi (VIEW_TYPE_SENT).
//Tin nhắn văn bản nhận (VIEW_TYPE_RECEIVED).
//Tin nhắn ảnh gửi (VIEW_TYPE_SENT_IMAGE).
//Tin nhắn ảnh nhận (VIEW_TYPE_RECEIVED_IMAGE).
// ChatAdapter hiển thị nội dung tin nhắn, thời gian, và ảnh hồ sơ (cho tin nhắn nhận), đồng thời xử lý các tin nhắn tạm (ảnh đang tải lên) và tải ảnh từ URL hoặc URI cục bộ bằng Picasso.
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_SENT = 1;
    public static final int VIEW_TYPE_RECEIVED = 2;
    public static final int VIEW_TYPE_SENT_IMAGE = 3;
    public static final int VIEW_TYPE_RECEIVED_IMAGE = 4;
    private final List<ChatMessage> chatMessages;
    private final String senderId;

    private Bitmap receiverProfileImage;

    public ChatAdapter(List<ChatMessage> chatMessages, Bitmap receiverProfileImage, String senderId) {
        this.chatMessages = chatMessages;
        this.receiverProfileImage = receiverProfileImage;
        this.senderId = senderId;
    }

    public void setReceiverProfileImage(Bitmap receiverProfileImage) {
        this.receiverProfileImage = receiverProfileImage;
    }

    @NonNull
    @Override
    // Tạo ViewHolder dựa trên viewType:
    // VIEW_TYPE_SENT: Sử dụng ItemContainerSentMessageBinding (layout item_container_sent_message.xml).
    // VIEW_TYPE_RECEIVED: Sử dụng ItemContainerReceivedMessageBinding (layout item_container_received_message.xml).
    // VIEW_TYPE_SENT_IMAGE: Sử dụng layout item_container_sent_image.xml
    // VIEW_TYPE_RECEIVED_IMAGE: Sử dụng layout item_container_received_image.xml.
    // Trả về ViewHolder tương ứng: SentMessageViewHolder, ReceivedMessageViewHolder, SentImageViewHolder, hoặc ReceivedImageViewHolder
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SENT) {

            return new SentMessageViewHolder(
                    ItemContainerSentMessageBinding.inflate(
                            LayoutInflater.from(parent.getContext()),
                            parent,
                            false
                    )
            );
        } else if (viewType == VIEW_TYPE_RECEIVED) {
            return new ReceivedMessageViewHolder(
                    ItemContainerReceivedMessageBinding.inflate(
                            LayoutInflater.from(parent.getContext()),
                            parent,
                            false
                    )
            );
        } else if (viewType == VIEW_TYPE_SENT_IMAGE) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_container_sent_image, parent, false);
            return new SentImageViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_container_received_image, parent, false);
            return new ReceivedImageViewHolder(view);
        }
    }

    @Override
    // Gắn dữ liệu vào ViewHolder dựa trên viewType
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = chatMessages.get(position);
        if (getItemViewType(position) == VIEW_TYPE_SENT) {
            ((SentMessageViewHolder) holder).setData(chatMessages.get(position));
        } else if (getItemViewType(position) == VIEW_TYPE_RECEIVED) {
            ((ReceivedMessageViewHolder) holder).setData(chatMessages.get(position), receiverProfileImage);
        } else if (holder.getClass() == SentImageViewHolder.class) {
            SentImageViewHolder viewHolder = (SentImageViewHolder) holder;
            viewHolder.textTime.setText(message.dateTime.equals("Đang gửi...") ? "Đang gửi..." : message.dateTime);
            // Hiển thị ảnh từ localImageUri nếu có
            if (message.localImageUri != null && !message.localImageUri.isEmpty()) {
                Picasso.get().load(Uri.parse(message.localImageUri)).into(viewHolder.image);
            } else {
                Picasso.get().load(message.message).into(viewHolder.image);
            }
        } else {
            ReceivedImageViewHolder viewHolder = (ReceivedImageViewHolder) holder;
            viewHolder.textTime.setText(message.dateTime.equals("Đang gửi...") ? "Đang gửi..." : message.dateTime);
            viewHolder.imageProfile.setImageBitmap(receiverProfileImage);
            if (message.localImageUri != null && !message.localImageUri.isEmpty()) {
                Picasso.get().load(Uri.parse(message.localImageUri)).into(viewHolder.image);
            } else {
                Picasso.get().load(message.message).into(viewHolder.image);
            }
        }
    }

    @Override
    public int getItemCount() {
        return chatMessages.size();
    }

    @Override
    // Xác định loại view
    public int getItemViewType(int position) {
        ChatMessage message = chatMessages.get(position);
        boolean isSender = message.senderId.equals(senderId);

        // Nếu là tin nhắn tạm có localImageUri, coi là ảnh
        if (message.localImageUri != null && !message.localImageUri.isEmpty()) {
            return isSender ? VIEW_TYPE_SENT_IMAGE : VIEW_TYPE_RECEIVED_IMAGE;
        }

        // Kiểm tra thông thường
        String content = message.message.toLowerCase();
        if (content.endsWith(".jpg") || content.endsWith(".jpeg") || content.endsWith(".png") || content.contains("cloudinary.com")) {
            return isSender ? VIEW_TYPE_SENT_IMAGE : VIEW_TYPE_RECEIVED_IMAGE;
        } else {
            return isSender ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
        }
    }


    // Sử dụng ItemContainerSentMessageBinding.
    // Hiển thị nội dung (textMessage) và thời gian (textTime)
    static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        private final ItemContainerSentMessageBinding binding;

        public SentMessageViewHolder(ItemContainerSentMessageBinding itemContainerSentMessageBinding) {
            super(itemContainerSentMessageBinding.getRoot());
            binding = itemContainerSentMessageBinding;
        }

        void setData(ChatMessage message) {
            binding.textMessage.setText(message.message);
            binding.textTime.setText(message.dateTime);
        }
    }

    // Sử dụng ItemContainerReceivedMessageBinding.
    // Hiển thị nội dung, thời gian, và ảnh hồ sơ (imageProfile)
    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        private final ItemContainerReceivedMessageBinding binding;

        public ReceivedMessageViewHolder(ItemContainerReceivedMessageBinding itemContainerReceivedMessageBinding) {
            super(itemContainerReceivedMessageBinding.getRoot());
            binding = itemContainerReceivedMessageBinding;
        }

        void setData(ChatMessage message, Bitmap receiverProfileImage) {
            binding.textMessage.setText(message.message);
            binding.textTime.setText(message.dateTime);
            if (receiverProfileImage != null) {
                binding.imageProfile.setImageBitmap(receiverProfileImage);
            }

        }
    }

    // Sử dụng layout item_container_sent_image.xml.
    // Chứa RoundedImageView (imageMessage) và TextView (textTime).
    static class SentImageViewHolder extends RecyclerView.ViewHolder {
        RoundedImageView image;
        TextView textTime;

        public SentImageViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.imageMessage);
            textTime = itemView.findViewById(R.id.textTime);
        }
    }

    // Sử dụng layout item_container_received_image.xml.
    // Chứa RoundedImageView (imageMessage, imageProfile) và TextView (textTime).
    static class ReceivedImageViewHolder extends RecyclerView.ViewHolder {
        RoundedImageView image;
        RoundedImageView imageProfile;
        TextView textTime;

        public ReceivedImageViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.imageMessage);
            imageProfile = itemView.findViewById(R.id.imageProfile);
            textTime = itemView.findViewById(R.id.textTime);

        }
    }
}
