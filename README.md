# WifiTVRemote

تطبيق Android للتحكم في أجهزة Android TV وGoogle TV المتوافقة عبر شبكة Wi-Fi المحلية فقط.

## المبدأ
- اكتشاف أجهزة Android TV على الشبكة المحلية.
- الاقتران برمز يظهر على التلفزيون.
- اتصال مشفر ببروتوكول Android TV Remote Protocol v2.
- أزرار الاتجاهات، OK، Back، Home، Menu، Volume، Power.
- لا يحتاج Bluetooth للتحكم بالتلفزيون.
- لا توجد خدمة سحابية أو حساب داخل التطبيق.

## ملاحظة التوافق
التطبيق مخصص لأجهزة Android TV/Google TV التي تدعم Android TV Remote Protocol v2. أجهزة Smart TV الأخرى مثل بعض أجهزة Samsung/LG/Roku ليست مضمونة.

## المصدر التقني
عملية البناء تستخدم مشروع Android TV Remote مفتوح المصدر المرخّص Apache-2.0 كمرجع لتنفيذ pairing وTLS وprotobuf، بدل إعادة تنفيذ التشفير من الصفر.

## البناء
GitHub Actions يبني APK debug وينشره كـRelease asset تلقائيًا.
