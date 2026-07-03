package sg.edu.nus.iss.wellness.wellness.csv;

/**
 *
 * RingConn 导出的三种 CSV 文件类型。PROJECT_SPEC.md §3 要求按表头识别类型，
 * 不要依赖文件名/列序号——文件名可能被用户改过，列顺序不同版本的 App 导出
 * 也可能不一样，只有列名是稳定的。
 */
public enum RingConnCsvType {
    ACTIVITY,
    VITAL_SIGNS,
    SLEEP,
    /** 表头三种都不匹配，整份文件不认识，导入时直接跳过这个文件 */
    UNKNOWN
}
