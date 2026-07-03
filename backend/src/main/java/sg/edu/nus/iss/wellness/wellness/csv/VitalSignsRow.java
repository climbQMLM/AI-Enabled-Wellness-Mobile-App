package sg.edu.nus.iss.wellness.wellness.csv;

/**
 * 对应 Vital Signs.csv 一行：心率/血氧/HRV 的均值最值。
 * spo2 的 % 号和 hr/hrv 不需要 strip(%) 的字段在解析阶段已经处理掉，
 * 这里存的都是去掉单位/符号后的纯数字。
 */
public record VitalSignsRow(
        Integer hrAvg, Integer hrMin, Integer hrMax,
        Integer spo2Avg, Integer spo2Min, Integer spo2Max,
        Integer hrvAvg, Integer hrvMin, Integer hrvMax
) {
}
