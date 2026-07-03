package sg.edu.nus.iss.wellness.wellness.csv;

/**
 * 对应 Activity.csv 一行：Date, Steps, Calories(kcal)
 */
public record ActivityRow(Integer steps, Integer caloriesKcal) {
}
