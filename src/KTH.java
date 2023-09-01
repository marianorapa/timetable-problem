import java.util.*;

/*
 * Represents all the persistent information from the input
 */
public class KTH {

  private Map<Integer, Room> rooms;
  private Map<Integer, Course> courses;
  private Map<Integer, StudentGroup> studentGroups;
  private Map<Integer, Lecturer> lecturers;
  private Map<Integer, Event> events;
  private ArrayList<Integer> eventIds;

  public KTH() {
    rooms = new HashMap<Integer, Room>();
    courses = new HashMap<Integer, Course>();
    studentGroups = new HashMap<Integer, StudentGroup>();
    lecturers = new HashMap<Integer, Lecturer>();
    events = new HashMap<Integer, Event>();
    eventIds = new ArrayList<Integer>();
  }

  public int addRoom(Room room) {
    rooms.put(room.getId(), room);
    return room.getId();
  }

  public Map<Integer, Room> getRooms() {
    return rooms;
  }

  public int getNumRooms() {
    return rooms.size();
  }

  public int addCourse(Course course) {
    courses.put(course.getId(), course);
    return course.getId();
  }

  public Map<Integer, Course> getCourses() {
    return courses;
  }

  public int addStudentGroup(StudentGroup studentGroup) {
    studentGroups.put(studentGroup.getId(), studentGroup);
    return studentGroup.getId();
  }

  public Map<Integer, StudentGroup> getStudentGroups() {
    return studentGroups;
  }

  public int addLecturer(Lecturer lecturer) {
    lecturers.put(lecturer.getId(), lecturer);
    return lecturer.getId();
  }

  public Map<Integer, Lecturer> getLecturers() {
    return lecturers;
  }

  public Event getEvent(int id) {
    return events.get(id);
  }

  public int getRandomEventId(Random rand) {
    return eventIds.get(rand.nextInt(eventIds.size()));
  }

  public Map<Integer, Event> getEvents() {
    return events;
  }

  public void createEvents() {
    // event group ids are unique
    int eventGroupID = 1;
    int lecturerNumber = 0;
    for (StudentGroup studentGroup : studentGroups.values()) {
      for (Course course : courses.values()) {
        List<Lecturer> possibleLecturers = new ArrayList<>();
        for (Lecturer lecturer : lecturers.values()) {
          if (lecturer.canTeach(course)) {
            possibleLecturers.add(lecturer);
          }
        }

        // create lecture events
        for (int i = 0; i < course.getNumLectures(); i++) {
          Event event = new Event(Event.Type.LECTURE,
              possibleLecturers.get(lecturerNumber % possibleLecturers.size()),
              course,
              eventGroupID);

          events.put(event.getId(), event);
          eventIds.add(event.getId());

          // update event group id
          eventGroupID++;
        }

      }
      lecturerNumber += 1;
    }
  }

  public void clear() {
    Room.resetId();
    Event.resetId();
    Lecturer.resetId();
    StudentGroup.resetId();
    rooms.clear();
    courses.clear();
    studentGroups.clear();
    lecturers.clear();
    events.clear();
    eventIds.clear();
  }

  public void printTimeTable(TimeTable tt) {
    StringBuilder sb = new StringBuilder();
    List<Integer> eventsCreated = new ArrayList<>();
    int nrSlots = 0;
    int nrEvents = 0;
    for (StudentGroupTimeTable rtt : tt.getSgTimeTables()) {
      sb.append("============ ");
      sb.append("Student group: ").append(rtt.getStudentGroup().getName());
      sb.append(" ============\n");
      for (int timeslot = 0; timeslot < StudentGroupTimeTable.NUM_TIMESLOTS; timeslot++) {
        for (int day = 0; day < StudentGroupTimeTable.NUM_DAYS; day++) {
          int eventId = rtt.getEvent(day, timeslot);
          if (eventId > nrEvents) {
            nrEvents = eventId;
          }
          nrSlots++;
          sb.append("| ").append(normalizeLength(events.get(eventId) != null ? events.get(eventId).getCourse().getName() : "Libre")).append(" |");
        }
        sb.append("\n");
        // Print teacher names
        for (int day = 0; day < StudentGroupTimeTable.NUM_DAYS; day++) {
          int eventId = rtt.getEvent(day, timeslot);
          if (eventId > nrEvents) {
            nrEvents = eventId;
          }
          nrSlots++;
          sb.append("| ").append(normalizeLength(events.get(eventId) != null ? " (" + events.get(eventId).getLecturer().getName() + ") " : " - ")).append(" |");
        }
        sb.append("\n");
        sb.append("\n");
      }
    }
    System.out.println(sb);

    System.out.println("--------");

    System.out.println("Number of slots: " + nrSlots);
    System.out.println("Number of events: " + nrEvents);
    System.out.println("Sparseness: " + ((double) nrEvents / (double) nrSlots));
  }

  private String normalizeLength(String name) {
    return center(name, 14);
  }

  public String center(String text, int len){
    String out = String.format("%"+len+"s%s%"+len+"s", "",text,"");
    float mid = (out.length()/2);
    float start = mid - (len/2);
    float end = start + len;
    return out.substring((int)start, (int)end);
  }

}
