package com.alda.translator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Entry(val source:String,val translated:String,val from:String,val to:String,val time:Long,val favorite:Boolean=false)
class HistoryStore(c:Context) {
    private val p=c.getSharedPreferences("alda_history",Context.MODE_PRIVATE)
    fun all():List<Entry> {
        val a=try{JSONArray(p.getString("items","[]"))}catch(_:Exception){JSONArray()}
        return (0 until a.length()).mapNotNull { i -> try {
            val o=a.getJSONObject(i); Entry(o.optString("s"),o.optString("t"),o.optString("f"),o.optString("to"),o.optLong("time"),o.optBoolean("fav"))
        } catch(_:Exception){null} }
    }
    fun add(e:Entry) { save((listOf(e)+all()).take(100)) }
    fun toggle(e:Entry) { save(all().map { if(it.time==e.time) it.copy(favorite=!it.favorite) else it }) }
    fun clear(){p.edit().remove("items").apply()}
    private fun save(list:List<Entry>) {
        val a=JSONArray(); list.forEach { e -> a.put(JSONObject().put("s",e.source).put("t",e.translated).put("f",e.from).put("to",e.to).put("time",e.time).put("fav",e.favorite)) }
        p.edit().putString("items",a.toString()).apply()
    }
}
