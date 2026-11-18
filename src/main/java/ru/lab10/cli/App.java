package ru.lab10.cli;

import ru.lab10.domain.*;
import java.time.LocalDate;
import java.util.*;

/** Console boundary; user roles are organizational, no authentication in the prototype. */
public class App {
    private final University university = new University("Воронежский государственный университет");
    private final Scanner input = new Scanner(System.in);

    public static void main(String[] args) {
        App app = new App();
        if (args.length == 1 && args[0].equals("--demo")) app.demo();
        else app.run();
    }

    public void run() {
        System.out.println("Университетская информационная система / лабораторная 10");
        while (true) {
            System.out.println("\n1 Структура | 2 Научный состав | 3 Декан | 4 Проекты | 5 Курсы | 6 Сводка | 0 Выход");
            if (!input.hasNextLine()) return;
            String choice = input.nextLine().strip();
            try {
                switch (choice) {
                    case "0": return;
                    case "1": manageStructure(); break;
                    case "2": manageEmployee(); break;
                    case "3": appointDean(); break;
                    case "4": assignProject(); break;
                    case "5": assignCourse(); break;
                    case "6": printSummary(); break;
                    default: System.out.println("Неизвестная команда");
                }
            } catch (NoSuchElementException ex) {
                System.out.println("Ввод завершён"); return;
            } catch (IllegalArgumentException | IllegalStateException | java.time.DateTimeException ex) {
                System.out.println("Ошибка: " + ex.getMessage());
            }
        }
    }

    private String read(String prompt) {
        System.out.print(prompt + ": ");
        return input.nextLine().strip();
    }

    private Faculty faculty(String name) {
        return university.getFaculties().stream().filter(f -> f.getName().equals(name)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Факультет не найден"));
    }

    private Institute institute() {
        Faculty f = faculty(read("Факультет"));
        String name = read("Кафедра");
        return f.getInstitutes().stream().filter(i -> i.getName().equals(name)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Кафедра не найдена"));
    }

    public void manageStructure() {
        String name = read("Название факультета (нового или существующего)");
        Faculty f = university.getFaculties().stream().filter(x -> x.getName().equals(name)).findFirst().orElse(null);
        // Validate all text before creating a faculty, to avoid a partial update on bad input.
        String department = read("Новая кафедра");
        String address = read("Адрес");
        if (name.isBlank() || department.isBlank() || address.isBlank())
            throw new IllegalArgumentException("Все поля обязательны");
        if (f == null) f = university.addFaculty(name);
        f.addInstitute(department, address);
        System.out.println("Структура сохранена. Назначьте декана перед получением сводки.");
    }

    public void manageEmployee() {
        String ssn = read("Социальный код (существующий — добавить кафедру)");
        Institute institute = institute();
        ResearchEmployee existing = university.getEmployees().stream()
            .filter(e -> e.getSocialSecurityNumber().equals(ssn)).findFirst().orElse(null);
        if (existing != null) existing.attachTo(institute);
        else {
            String name = read("ФИО");
            String email = read("E-mail");
            String area = read("Область исследований");
            String role = read("Преподаватель? да/нет");
            if (!role.equalsIgnoreCase("да") && !role.equalsIgnoreCase("нет"))
                throw new IllegalArgumentException("Введите да или нет");
            university.registerEmployee(ssn, name, email, area, institute, role.equalsIgnoreCase("да"));
        }
        System.out.println("Научный состав обновлён");
    }

    public void appointDean() {
        Faculty f = faculty(read("Факультет"));
        f.appointDean(university.findEmployee(read("Социальный код декана")));
        System.out.println("Декан назначен");
    }

    public void assignProject() {
        String name = read("Название проекта (нового или существующего)");
        Project project = university.getProjects().stream().filter(p -> p.getName().equals(name)).findFirst().orElse(null);
        if (project == null) {
            LocalDate start = LocalDate.parse(read("Дата начала YYYY-MM-DD"));
            LocalDate end = LocalDate.parse(read("Дата окончания YYYY-MM-DD"));
            project = university.createProject(name, start, end);
        }
        university.assignToProject(read("Социальный код сотрудника"), project, Integer.parseInt(read("Часы участия")));
        System.out.println("Участие в проекте сохранено");
    }

    public void assignCourse() {
        String ssn = read("Социальный код преподавателя");
        String id = read("Уникальный ID курса");
        String name = read("Название курса");
        university.assignCourse(ssn, id, name, Integer.parseInt(read("Часов в неделю")));
        System.out.println("Курс назначен");
    }

    public void printSummary() {
        university.validateReady();
        System.out.println("\n" + university.getName());
        System.out.println("Сотрудников научно-педагогического состава: " + university.getNumberOfEmployees());
        for (Faculty f : university.getFaculties()) {
            System.out.println("Факультет: " + f.getName() + "; декан: " + f.getDean().getName());
            for (Institute i : f.getInstitutes()) System.out.println("  Кафедра: " + i.getName() + "; " + i.getAddress());
        }
        for (ResearchEmployee e : university.getEmployees()) {
            System.out.println("Сотрудник: " + e.getSocialSecurityNumber() + " | " + e.getName() + " | " + e.getEmail());
            System.out.println("  Исследования: " + e.getResearchArea());
            for (Institute i : e.getInstitutes()) System.out.println("  Прикрепление: " + i.getName());
            for (Participation p : e.getParticipations()) {
                Project project = p.getProject();
                System.out.println("  Проект: " + project.getName() + " | " + project.getStartingDate() + " — "
                    + project.getEndDate() + " | " + p.getHours() + " ч.");
            }
            if (e instanceof Lecturer) for (Course c : ((Lecturer)e).getCourses())
                System.out.println("  Курс: " + c.getId() + " | " + c.getName() + " | " + c.getWeeklyDuration() + " ч./нед.");
        }
    }

    public void demo() {
        System.out.println("Университетская информационная система — демонстрация UC-01…UC-06");
        Faculty f = university.addFaculty("ФКН");
        Institute i = f.addInstitute("Программирование и ИТ", "Университетская пл., 1");
        Institute second = f.addInstitute("Информационные системы", "Университетская пл., 1");
        ResearchEmployee dean = university.registerEmployee("R-001", "Иванов Иван Иванович", "ivanov@vsu.ru", "Инженерия ПО", i, true);
        ResearchEmployee researcher = university.registerEmployee("R-002", "Петрова Анна Сергеевна", "petrova@vsu.ru", "Анализ данных", second, false);
        dean.attachTo(second);
        f.appointDean(dean);
        Project p = university.createProject("Цифровой университет", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31));
        university.assignToProject(dean.getSocialSecurityNumber(), p, 120);
        university.assignToProject(researcher.getSocialSecurityNumber(), p, 80);
        university.assignCourse("R-001", "CS-101", "Проектирование информационных систем", 4);
        printSummary();
        System.out.println("\nПроверка ограничения: курс может вести только преподаватель");
        try { university.assignCourse("R-002", "CS-102", "UML", 2); }
        catch (IllegalArgumentException ex) { System.out.println("Ожидаемый отказ: " + ex.getMessage()); }
        System.out.println("Демонстрация завершена успешно.");
    }
}
