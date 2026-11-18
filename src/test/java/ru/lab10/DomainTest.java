package ru.lab10;

import ru.lab10.domain.*;
import java.time.LocalDate;

/** Invariant and rejection tests; no third-party dependencies. */
public class DomainTest {
    private static int checks;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    private static void rejects(Runnable action, String label) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException ex) { checks++; return; }
        throw new AssertionError("Expected rejection: " + label);
    }
    public static void main(String[] args) {
        University u = new University("ВГУ");
        rejects(u::validateReady, "empty university");
        Faculty f = u.addFaculty("ФКН");
        Institute i = f.addInstitute("ПиИТ", "Университетская пл., 1");
        ResearchEmployee r = u.registerEmployee("R1", "Исследователь", "r@vsu.ru", "ИИ", i, false);
        Lecturer l = (Lecturer) u.registerEmployee("L1", "Преподаватель", "l@vsu.ru", "UML", i, true);
        Lecturer l2 = (Lecturer) u.registerEmployee("L2", "Преподаватель 2", "l2@vsu.ru", "ООП", i, true);
        check(u.getNumberOfEmployees() == 3, "derived count");
        check(r.getInstitutes().size() == 1, "mandatory institute");
        rejects(() -> u.registerEmployee("R1", "Другой", "a@vsu.ru", "ИИ", i, false), "unique ssn");
        rejects(() -> u.registerEmployee("BAD", "", "wrong", "ИИ", i, false), "invalid employee");
        check(u.getNumberOfEmployees() == 3, "no insertion on failure");
        rejects(() -> u.registerEmployee("X", "X", "x@vsu.ru", "ИИ", null, false), "null institute");
        University other = new University("Другой");
        Institute foreign = other.addFaculty("Другой").addInstitute("Кафедра", "Адрес");
        rejects(() -> r.attachTo(foreign), "foreign institute");
        rejects(() -> u.registerEmployee("X", "X", "x@vsu.ru", "ИИ", foreign, false), "foreign registration");
        Institute second = f.addInstitute("ИС", "Адрес");
        r.attachTo(second); r.attachTo(second);
        check(r.getInstitutes().size() == 2, "multiple institutes, no duplicates");
        rejects(() -> r.getInstitutes().clear(), "cannot remove mandatory membership");
        rejects(() -> u.getEmployees().clear(), "readonly registry");
        rejects(u::validateReady, "dean required");
        f.appointDean(l); u.validateReady(); checks++;
        Faculty f2 = u.addFaculty("Математический");
        f2.addInstitute("Алгебра", "Адрес");
        rejects(() -> f2.appointDean(l), "at most one faculty per dean");
        f2.appointDean(r); u.validateReady(); checks++;
        rejects(() -> f.appointDean(null), "dean belongs to university");
        LocalDate start = LocalDate.of(2026, 9, 1), end = LocalDate.of(2027, 8, 31);
        rejects(() -> u.createProject("Bad", end, start), "project dates");
        Project p = u.createProject("Проект", start, end);
        rejects(() -> u.assignToProject("R1", p, 0), "positive hours");
        check(r.getParticipations().isEmpty(), "no partial participation");
        u.assignToProject("R1", p, 80);
        u.assignToProject("L1", p, 120);
        check(r.getParticipations().get(0).getHours() == 80 && l.getParticipations().get(0).getHours() == 120, "hours per pair");
        rejects(() -> u.assignToProject("R1", p, 20), "unique participation pair");
        rejects(() -> u.assignToProject("missing", p, 20), "missing employee");
        Project foreignProject = other.createProject("Другой", start, end);
        rejects(() -> u.assignToProject("R1", foreignProject, 20), "foreign project");
        rejects(() -> u.assignCourse("R1", "C1", "UML", 4), "only lecturer teaches");
        rejects(() -> u.assignCourse("L1", "C1", "UML", 0), "positive weekly duration");
        check(u.getCourses().isEmpty() && l.getCourses().isEmpty(), "no partial course");
        Course c = u.assignCourse("L1", "C1", "UML", 4);
        rejects(() -> u.assignCourse("L1", "C1", "UML", 4), "duplicate lecturer-course pair");
        rejects(() -> u.assignCourse("L2", "C1", "Other", 4), "unique course ID");
        check(l2.getCourses().isEmpty() && c.getLecturers().size() == 1, "rejection leaves course unchanged");
        check(u.assignCourse("L2", "C1", "UML", 4) == c, "shared course object");
        check(c.getLecturers().size() == 2 && l2.getCourses().contains(c), "many to many course assignment");
        rejects(() -> c.getLecturers().clear(), "readonly course lecturers");
        rejects(() -> f.addInstitute("ПиИТ", "Адрес"), "unique department name");
        rejects(() -> u.addFaculty("ФКН"), "unique faculty name");
        System.out.println("PASS: " + checks + " проверок ограничений предметной модели");
    }
}
