import java.util.*;

// keeps all the TimeTables for a generation
public class Population {

  // time slot class used when creating the random population
  public class TimeSlot {
    private int sgId; // Student group id
    private int day;
    private int timeSlot;
    private boolean available = true;
    public TimeSlot(int sgId, int day, int timeSlot) {
      this.sgId = sgId;
      this.day = day;
      this.timeSlot = timeSlot;
    }
  }

  // should be ordered when selecting the best individuals
  private LinkedList<TimeTable> individuals;

  public Population() {
    individuals = new LinkedList<TimeTable>();
  }

  public void createRandomIndividuals(int numIndividuals, KTH kth) {
    Map<Integer, StudentGroup> studentGroups = kth.getStudentGroups();

    for(int i = 0; i < numIndividuals; i++) {
      // register all available timeslots
      ArrayList<TimeSlot> availableTimeSlots = new ArrayList<TimeSlot>();
      for(int sgId : studentGroups.keySet()) {
        for(int d = 0; d < StudentGroupTimeTable.NUM_DAYS; d++) {
          for(int t = 0; t < StudentGroupTimeTable.NUM_TIMESLOTS; t++) {
            // Timeslot es ahora un dia y hora de un grupo de estudiantes
            availableTimeSlots.add(new TimeSlot(sgId, d, t));
          }
        }
      }

      TimeTable tt = new TimeTable(studentGroups.size());
      for (int sgId : studentGroups.keySet()) {
        StudentGroup studentGroup = studentGroups.get(sgId);
        StudentGroupTimeTable studentGroupTimeTable = new StudentGroupTimeTable(studentGroup);
        tt.putSgTimeTable(sgId, studentGroupTimeTable);
      }

      // index variables
      int rttId = 0;
      int day = 0;
      int timeSlot = 0;

      // assign all event to any randomly selected available timeslot
      Random rand = new Random(System.currentTimeMillis());
      for (Event e : kth.getEvents().values()) {
        TimeSlot availableTimeSlot = availableTimeSlots.get(rand.nextInt(availableTimeSlots.size()));
        StudentGroupTimeTable sgTimeTable = tt.getSgTimeTables()[availableTimeSlot.sgId];
        sgTimeTable.setEvent(availableTimeSlot.day, availableTimeSlot.timeSlot, e.getId());
        availableTimeSlots.remove(availableTimeSlot);
        /* DEBUG
        System.out.println("==============");
        System.out.println("ROOM TIME TABLE ID: " + rtt.getRoom().getName());
        System.out.println("Day: " + availableTimeSlot.day + " Timeslot: " + availableTimeSlot.timeSlot + " Event ID: " + e.getId());
        */
      }
      individuals.add(tt);
      availableTimeSlots.clear();
    }
  }

  // assumes sorted
  public TimeTable getTopIndividual() {
    return individuals.get(0);
  }

  public TimeTable getWorstIndividual() {
    return individuals.getLast();
  }

  public void addIndividual(TimeTable tt) {
    individuals.add(tt);
  }

  public TimeTable getIndividual(int i) {
    return individuals.get(i);
  }

  public void addIndividualSorted(TimeTable tt) {
    ListIterator<TimeTable> it = individuals.listIterator();
    ListIterator<TimeTable> it2 = individuals.listIterator();

    while (it.hasNext()) {
      if (it.next().getFitness() < tt.getFitness()) {
        it2.add(tt);
        break;
      }

      it2.next();
    }
  }

  public ListIterator<TimeTable> listIterator() {
    return individuals.listIterator();
  }

  public void sortIndividuals() {
    Collections.sort(individuals);
  }

  public int size() {
    return individuals.size();
  }
}
