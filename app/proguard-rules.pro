# Gson 反射反序列化:保留应用内所有类的成员名(数据类字段名必须与 JSON key 对应,
# 被 R8 混淆后 Gson 会报 "Abstract classes can't be instantiated" 等错误)
-keep class com.galstruo.app.** { *; }

# Gson 官方建议(反射走 Unsafe)
-keep class sun.misc.Unsafe { *; }

# Gson 类型适配器注册用反射查找,保留
-keepattributes Signature
-keepattributes *Annotation*
