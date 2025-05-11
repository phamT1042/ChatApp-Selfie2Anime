package com.example.chatappfirebase.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappfirebase.R;
import com.example.chatappfirebase.databinding.ItemContainerRecentConversionBinding;
import com.example.chatappfirebase.listeners.ConversionListener;
import com.example.chatappfirebase.models.ChatMessage;
import com.example.chatappfirebase.models.User;
import com.example.chatappfirebase.utilities.Constants;
import com.example.chatappfirebase.utilities.PreferenceManager;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Objects;

public class RecentConversationAdapter extends RecyclerView.Adapter<RecentConversationAdapter.ConversionViewHolder> {

    private final List<ChatMessage> chatMessages;
    private final ConversionListener conversionListener;
    private FirebaseFirestore database;

    private final PreferenceManager preferenceManager;

    public RecentConversationAdapter(List<ChatMessage> chatMessages, ConversionListener conversionListener, PreferenceManager preferenceManager) {
        this.chatMessages = chatMessages;
        this.conversionListener = conversionListener;
        this.preferenceManager = preferenceManager;
        database = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public ConversionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ConversionViewHolder(
                ItemContainerRecentConversionBinding.inflate(
                        LayoutInflater.from(parent.getContext()),
                        parent,
                        false
                )
        );
    }

    @Override
    public void onBindViewHolder(@NonNull ConversionViewHolder holder, int position) {
        holder.setData(chatMessages.get(position));
        holder.listenAvailabilityOfUser(chatMessages.get(position));
    }

    @Override
    public int getItemCount() {
        return chatMessages.size();
    }

    class ConversionViewHolder extends RecyclerView.ViewHolder {
        ItemContainerRecentConversionBinding binding;
        long receiverAvailability;
        long senderAvailability;

        public ConversionViewHolder(ItemContainerRecentConversionBinding itemContainerRecentConversionBinding) {
            super(itemContainerRecentConversionBinding.getRoot());
            binding = itemContainerRecentConversionBinding;
        }

        private void listenAvailabilityOfUser(ChatMessage chatMessage) {
            String currentUserId = preferenceManager.getString(Constants.KEY_USER_ID);
            String otherUserId = chatMessage.senderId.equals(currentUserId) ? chatMessage.receiverId : chatMessage.senderId;

            database.collection(Constants.KEY_COLLECTION_USERS)
                    .document(otherUserId)
                    .addSnapshotListener((value, error) -> {
                        if (error != null) return;
                        if (value != null && value.getLong(Constants.KEY_AVAILABILITY) != null) {
                            long availability = value.getLong(Constants.KEY_AVAILABILITY);
                            int colorId = (availability == 1) ? R.color.online : R.color.offline;
                            binding.iconOnOff.setColorFilter(ContextCompat.getColor(binding.getRoot().getContext(), colorId));
                        }
                    });
        }

        void setData(ChatMessage chatMessage) {
            binding.textName.setText(chatMessage.conversionName);
            binding.textRecentMessage.setText(chatMessage.message);
            binding.ProfileImage.setImageBitmap(getConversionImage(chatMessage.conversionImage));
            binding.getRoot().setOnClickListener(view -> {
                User user = new User();
                user.id = chatMessage.conversionId;
                user.name = chatMessage.conversionName;
                user.image = chatMessage.conversionImage;
                conversionListener.onConversionClicked(user);
            });
        }
    }

    private Bitmap getConversionImage(String imageString) {
        try {
            byte[] bytes = Base64.decode(imageString, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception exception) {
            exception.printStackTrace();
            return null;
        }
    }
}
