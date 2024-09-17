package com.example.shuntingapp.dsform

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.shuntingapp.R

class ShuntingFormAdapter(private val dataList: MutableList<ShuntingForm>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    class FormHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val hookTextView: TextView = view.findViewById(R.id.hookTV)
        val trackTextView: TextView = view.findViewById(R.id.trackTV)
        val pnTextView: TextView = view.findViewById(R.id.pnTV)
        val countTextView: TextView = view.findViewById(R.id.countTV)
        val contentTextView: TextView = view.findViewById(R.id.contentTV)
    }

    class FormDataViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val hookTextView: TextView = view.findViewById(R.id.hookTV)
        val trackTextView: TextView = view.findViewById(R.id.trackTV)
        val pnTextView: TextView = view.findViewById(R.id.pnTV)
        val countTextView: TextView = view.findViewById(R.id.countTV)
        val contentTextView: TextView = view.findViewById(R.id.contentTV)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.activity_shunting_form_header, parent, false)
            FormHeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.activity_shunting_form_item, parent, false)
            FormDataViewHolder(view)
        }
    }

    override fun getItemCount(): Int {
        return dataList.size + 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is FormHeaderViewHolder) {
            holder.hookTextView.text = "勾序"
            holder.trackTextView.text = "股道"
            holder.pnTextView.text = "+/-"
            holder.countTextView.text = "辆数"
            holder.contentTextView.text = "记事"
        } else if (holder is FormDataViewHolder) {
            val data = dataList[position - 1]
            holder.hookTextView.text = data.hook.toString()
            holder.trackTextView.text = data.track
            holder.pnTextView.text = data.pn
            holder.countTextView.text = data.count.toString()
            holder.contentTextView.text = data.content
        }
    }

    fun addData(newData: List<ShuntingForm>) {
        dataList.clear()  // 清除旧数据
        dataList.addAll(newData)  // 添加新数据
        notifyDataSetChanged()  // 通知数据已改变，刷新整个列表
    }
}