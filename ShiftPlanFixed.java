// 5.1 Исправленный вариант по замечаниям из ReviewNotes.csv
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ShiftPlanFixed {

    private static final double MAX_HOURS_PER_SHIFT = 10.0;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final List<String> log = new ArrayList<>();

    public static void main(String[] args) {
        ShiftPlanFixed planner = new ShiftPlanFixed();

        // Исходный набор данных из ShiftPlanLegacy
        List<Brigade> brigades = new ArrayList<>();
        brigades.add(new Brigade("Иванов", 11.0, true));
        brigades.add(new Brigade("Петров", 0.0, true));
        brigades.add(new Brigade("Сидоров", 4.5, false));
        brigades.add(new Brigade("Козлов", 8.0, true));

        // Корректный сценарий: после фильтрации доступно ровно 2 бригады.
        // Это воспроизводит состав наряда исходной версии при need = 3
        // (legacy молча обрезала список до 2 доступных).
        StringWriter sink = new StringWriter();
        String naryad = planner.make(brigades, 2, sink);

        System.out.println("Наряд: " + naryad);
        System.out.println("Файл: " + sink.toString());

        // Сценарий, который в legacy молча обрезал список, теперь даёт
        // осмысленное исключение — реализация замечания №2.
        try {
            planner.make(brigades, 3, new StringWriter());
        } catch (IllegalArgumentException e) {
            System.out.println("Ожидаемое исключение: " + e.getMessage());
        }

        System.out.println("Журнал: " + planner.log);
        System.out.println();

        runChecks();
    }

    // Замечания 1, 2, 3, 5, 6, 7: фильтрация без CME, проверка need,
    // запись через переданный Writer, разделение ответственности.
    public String make(List<Brigade> brigades, int need, Writer writer) {
        if (brigades == null) {
            throw new IllegalArgumentException("список бригад не задан");
        }
        if (need < 0) {
            throw new IllegalArgumentException("число бригад в наряде отрицательно: " + need);
        }

        List<Brigade> eligible = filterEligible(brigades);
        if (need > eligible.size()) {
            throw new IllegalArgumentException(
                    "требуется бригад: " + need + ", доступно: " + eligible.size());
        }

        String naryad = formatNaryad(eligible, need);
        writeNaryad(writer, naryad);
        logEvent("наряд сформирован");
        return naryad;
    }

    static List<Brigade> filterEligible(List<Brigade> brigades) {
        List<Brigade> eligible = new ArrayList<>();
        for (Brigade brigade : brigades) {
            if (isEligible(brigade)) {
                eligible.add(brigade);
            }
        }
        return eligible;
    }

    static boolean isEligible(Brigade brigade) {
        return brigade.admitted && brigade.hoursPerShift <= MAX_HOURS_PER_SHIFT;
    }

    static String formatNaryad(List<Brigade> eligible, int need) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < need; i++) {
            result.append(eligible.get(i).lastName).append("; ");
        }
        return result.toString();
    }

    void writeNaryad(Writer writer, String naryad) {
        if (writer == null) {
            throw new IllegalArgumentException("writer не задан");
        }
        try {
            writer.write(naryad);
            writer.flush();
        } catch (IOException e) {
            throw new IllegalStateException("не удалось записать наряд", e);
        }
    }

    void logEvent(String message) {
        log.add(LocalDate.now().format(DATE_FORMAT) + " " + message);
    }

    // --- самопроверки ---

    private static int failedChecks = 0;

    static void runChecks() {
        ShiftPlanFixed planner = new ShiftPlanFixed();

        List<Brigade> base = new ArrayList<>();
        base.add(new Brigade("Иванов", 11.0, true));
        base.add(new Brigade("Петров", 0.0, true));
        base.add(new Brigade("Сидоров", 4.5, false));
        base.add(new Brigade("Козлов", 8.0, true));

        // Характеризующий тест: эталон снят с исходной версии ShiftPlanLegacy.
        // Legacy при need = 3 молча обрезала наряд до 2 доступных бригад
        // и печатала "Петров; Козлов; ". В исправленной версии то же поведение
        // воспроизводится при корректном need = 2, а need = 3 даёт исключение.
        String naryad = planner.make(copy(base), 2, new StringWriter());
        check("состав наряда совпадает с исходной версией",
                "Петров; Козлов; ".equals(naryad));

        check("журнал содержит запись о формировании наряда",
                planner.log.size() == 1 && planner.log.get(0).endsWith("наряд сформирован"));

        check("слишком большой need даёт IllegalArgumentException",
                throwsIllegalArgument(() -> planner.make(copy(base), 5, new StringWriter())));

        check("отрицательный need даёт IllegalArgumentException",
                throwsIllegalArgument(() -> planner.make(copy(base), -1, new StringWriter())));

        check("null-список даёт IllegalArgumentException",
                throwsIllegalArgument(() -> planner.make(null, 1, new StringWriter())));

        check("null-writer даёт IllegalArgumentException",
                throwsIllegalArgument(() -> planner.make(copy(base), 1, null)));

        check("недопущенная бригада не попадает в наряд",
                planner.make(list(new Brigade("Сидоров", 4.5, false),
                                  new Brigade("Петров", 0.0, true)),
                             1, new StringWriter()).equals("Петров; "));

        check("бригада со сверхнормативными часами не попадает в наряд",
                planner.make(list(new Brigade("Иванов", 11.0, true),
                                  new Brigade("Козлов", 8.0, true)),
                             1, new StringWriter()).equals("Козлов; "));

        if (failedChecks > 0) {
            throw new AssertionError("не пройдено проверок: " + failedChecks);
        }
        System.out.println();
        System.out.println("Все проверки пройдены.");
    }

    interface Action { void run(); }

    static boolean throwsIllegalArgument(Action action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    static List<Brigade> copy(List<Brigade> source) {
        return new ArrayList<>(source);
    }

    static List<Brigade> list(Brigade... items) {
        List<Brigade> result = new ArrayList<>();
        for (Brigade item : items) {
            result.add(item);
        }
        return result;
    }

    static void check(String name, boolean passed) {
        System.out.println((passed ? "[OK]   " : "[FAIL] ") + name);
        if (!passed) {
            failedChecks++;
        }
    }
}

class Brigade {
    final String lastName;
    final double hoursPerShift;
    final boolean admitted;

    Brigade(String lastName, double hoursPerShift, boolean admitted) {
        this.lastName = lastName;
        this.hoursPerShift = hoursPerShift;
        this.admitted = admitted;
    }
}
