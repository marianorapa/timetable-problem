public class StudentGroupTimeTable {

  public static final int NUM_DAYS = 5;
  public static final int NUM_TIMESLOTS = 4;

  // matrix of timeslots
  // with id of what event that is bookes in each slot
  // rows are timeslots, columns are days
  private int[][] timeSlots;

  private StudentGroup studentGroup;

  public StudentGroupTimeTable(StudentGroup studentGroup) {
    this.studentGroup = studentGroup;
    timeSlots = new int[NUM_TIMESLOTS][NUM_DAYS];
  }

  public boolean hasEvent(int day, int timeslot) {
    if(timeSlots[timeslot][day] == 0) {
      return false;
    } else {
      return true;
    }
  }

  public int getEvent(int day, int timeslot) {
    return timeSlots[timeslot][day];
  }

  public void setEvent(int day, int timeslot, int eventId) {
    timeSlots[timeslot][day] = eventId;
  }

  public StudentGroup getStudentGroup() {
    return studentGroup;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Student group: " + studentGroup.getName() + "\n");
    for (int timeslot = 0; timeslot < NUM_TIMESLOTS; timeslot++) {
      for (int day = 0; day < NUM_DAYS; day++) {
        sb.append("[\t" + timeSlots[timeslot][day] + "\t]");
      }
      sb.append("\n");
    }

    return sb.toString();
  }
}
