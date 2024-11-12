package com.app.facerecognizer.adapter;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.facerecognizer.R;
import com.app.facerecognizer.db.entities.FaceImageInfo;

import java.util.List;

public class FaceImageListAdapter extends RecyclerView.Adapter<FaceImageListAdapter.ViewHolder> {

    private List<FaceImageInfo> list;

    public FaceImageListAdapter(List<FaceImageInfo> mList) {
        list = mList;
    }

    public OnItemLongClickListener onItemLongClickListener;

    @NonNull
    @Override
    public FaceImageListAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_face_image_list_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FaceImageListAdapter.ViewHolder holder, @SuppressLint("RecyclerView") int position) {
        Bitmap bitmap = BitmapFactory.decodeFile(list.get(position).getPath());
        holder.faceView.setImageBitmap(bitmap);

        holder.itemView.setOnLongClickListener(v -> {
            // 通过回调传递点击的条目
            if (onItemLongClickListener != null) {
                onItemLongClickListener.onItemLongClick(list.get(position));
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }


    public class ViewHolder extends RecyclerView.ViewHolder {
        ImageView faceView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            faceView = itemView.findViewById(R.id.face_image);
        }
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(FaceImageInfo faceImageInfo);  // 点击时传递的参数是当前的 FaceImageInfo 对象
    }
}
