## note
1. `KVConfigManager` 是一个配置类，里面主要通过 `HashMap<String/* Namespace */, HashMap<String/* Key */, String/* Value */>>` 维护数据。
支持通过json方式，序列化到文件，以及load从文件加载等。 `KVConfigSerializeWrapper` 是其一个包装工具类。
2. 