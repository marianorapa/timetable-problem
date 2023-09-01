public class TimeTable implements Comparable<TimeTable> {
  private int fitness;

  // The timetables for each room
  private StudentGroupTimeTable[] sgTimeTables;

  public TimeTable(int numRooms) {
    sgTimeTables = new StudentGroupTimeTable[numRooms];
  }

  public int getFitness() {
    return fitness;
  }

  public void setFitness(int fitness) {
    this.fitness = fitness;
  }

  public StudentGroupTimeTable[] getSgTimeTables() {
    return sgTimeTables;
  }

  public void putSgTimeTable(int i, StudentGroupTimeTable rtt) {
    sgTimeTables[i] = rtt;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (StudentGroupTimeTable rtt : sgTimeTables) {
      sb.append(rtt.toString());
      sb.append("\n");
    }

    return sb.toString();
  }
  
  // sorts descending
  @Override
  public int compareTo(TimeTable other) {
    int otherFitness = other.getFitness();

    if (fitness > otherFitness)
      return -1;
    else if (fitness == otherFitness)
      return 0;
    else
      return 1;
  }
}
