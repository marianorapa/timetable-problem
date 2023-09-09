import java.util.*;
import java.io.*;

/**
 * Performs the Genetic Algorithm(GA) on the KTH data set.
 */
public class GA {

  private int maxGenerations = 10000;
  private int finalGenerations;

  public int getFinalGenerations() {
    return finalGenerations;
  }

  public void setMaxGenerations(int maxGenerations) {
    this.maxGenerations = maxGenerations;
  }

  public enum SELECTION_TYPE {
    NORMAL,
    ROULETTE_WHEEL,
    TOURNAMENT;

    public static String[] getNames() {
      GA.SELECTION_TYPE[] states = values();
      String[] names = new String[states.length];
      for (int i = 0; i < states.length; i++) {
          names[i] = states[i].name();
      }
      return names;
    }
  };

  public enum MUTATION_TYPE {
    NORMAL;

    public static String[] getNames() {
      GA.MUTATION_TYPE[] states = values();
      String[] names = new String[states.length];
      for (int i = 0; i < states.length; i++) {
          names[i] = states[i].name();
      }
      return names;
    }
  };

  // algorithm parameters
  private int DESIRED_FITNESS;
  private int MAX_POPULATION_SIZE;
  private int MUTATION_PROBABILITY; // compared with 1000
  private int CROSSOVER_PROBABILITY; // compared with 1000 - NOT USED
  private int SELECTION_SIZE;
  private int TOURNAMENT_POOL_SIZE = 5;
  private SELECTION_TYPE selectionType = SELECTION_TYPE.NORMAL;
  private MUTATION_TYPE mutationType = MUTATION_TYPE.NORMAL;

  private Population population;
  private KTH kth;

  public GA() {
    kth = new KTH();
  }
  
  /*
  * Returns a schedule based on the given constraints
  */
  public TimeTable generateTimeTable() throws MaxGenerationsExceededException {
    // run until the fitness is high enough
    // high enough should at least mean that
    // all hard constraints are solved
    // adjust for the number of soft constraints to be solved too
    // use another stop criteria too, in order to not run forever?

    // create the initial random population
    createRandomPopulation();
    ListIterator<TimeTable> it = population.listIterator();
    while(it.hasNext()) {
      TimeTable tt = it.next();
      fitness(tt);
    }

    population.sortIndividuals();

    int numGenerations = 1;
    while (population.getTopIndividual().getFitness() < DESIRED_FITNESS && numGenerations < maxGenerations) {
      Population children = breed(population, MAX_POPULATION_SIZE);
      population = selection(population, children);

      // sort the population by their fitness
      // not needed
      population.sortIndividuals(); 
      
      numGenerations++;
      System.out.println("#GENERATIONS: " + numGenerations + " BEST FITNESS: " + population.getTopIndividual().getFitness());
    }

    if (numGenerations >= maxGenerations) {
      throw new MaxGenerationsExceededException(maxGenerations);
    }
    this.finalGenerations = numGenerations;

    return population.getTopIndividual();
  }


  //////////////////////////
  // SETUP
  //////////////////////////

  public void loadData(String dataFileUrl) {
    kth.clear(); // reset all previous data before loading

    try {
      File file = new File(dataFileUrl);
      BufferedReader in = new BufferedReader(new FileReader(file));
      String line = null;
      // input data sections are read in the following order separated by #
      // #rooms <name> <capacity> <type>
      // #courses <id> <name> <numLectures> <numClasses> <numLabs>
      // #lecturers <name> <course>+
      // #studentgroups <name> <numStudents> <course>+
      String readingSection = null;
      String roomName = null;
      String courseName = null;
      String lecturerName = null;
      String studentGroupName = null;
      HashMap<String, Integer> courseNameToId = new HashMap<String, Integer>();
      while((line = in.readLine()) != null) {
        String[] data = line.split(" ");
        if(data[0].charAt(0) == '#') {
          readingSection = data[1];
          data = in.readLine().split(" ");
        }
        if(readingSection.equals("ROOMS")) {
          roomName = data[0];
          int cap = Integer.parseInt(data[1]);
          Event.Type type = Event.generateType(Integer.parseInt(data[2]));
          Room room = new Room(roomName, cap, type);
          kth.addRoom(room);
        } else if(readingSection.equals("COURSES")) {
          courseName = data[0];
          int numLectures = Integer.parseInt(data[1]);
          int numLessons = Integer.parseInt(data[2]);
          int numLabs = Integer.parseInt(data[3]);
          Course course = new Course(courseName, numLectures, numLessons, numLabs);
          courseNameToId.put(courseName, course.getId());
          kth.addCourse(course);
        } else if(readingSection.equals("LECTURERS")) {
          lecturerName = data[0];
          Lecturer lecturer = new Lecturer(lecturerName);
          for(int i = 1; i < data.length; i++) {
            // register all courses that this lecturer may teach
            courseName = data[i];
            lecturer.addCourse(kth.getCourses().get(courseNameToId.get(courseName)));
          }
          kth.addLecturer(lecturer);
        } else if(readingSection.equals("STUDENTGROUPS")) {
          studentGroupName = data[0];
          int size = Integer.parseInt(data[1]);
          StudentGroup studentGroup = new StudentGroup(studentGroupName, size);
          for(int i = 2; i < data.length; i++) {
            courseName = data[i];
            studentGroup.addCourse(kth.getCourses().get(courseNameToId.get(courseName)));
          }
          kth.addStudentGroup(studentGroup);
        }
      }
      kth.createEvents(); // create all events
      in.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  //////////////////////////
  // GENETIC ALGORITHMS
  //////////////////////////

  private Population createRandomPopulation() {
    population = new Population();
    population.createRandomIndividuals(MAX_POPULATION_SIZE, kth);
    return population;
  }

  private TimeTable next(ListIterator<TimeTable> it) {
    return it.hasNext() ? it.next() : null;
  }

  /////////////////////////////
  
  // uses another implementation of roulette selection of parents
  private Population breed(Population population, int N) {
    Population children = new Population();

    // calculate the pseudofitness of each individual
    // used in the roulette selection
    int[] pseudoFitness = new int[population.size()];
    int smallestFitness = population.getWorstIndividual().getFitness();
    smallestFitness = smallestFitness >= 0 ? 0 : smallestFitness;
    
    int i = 0;
    ListIterator<TimeTable> it = population.listIterator();
    int fitnessSum = 0;
    while (it.hasNext()) {
      // the smallest possible is 1, this saves us from weird behaviour in
      // cases where all individuals have the same fitness
      pseudoFitness[i] = it.next().getFitness() + -1 * smallestFitness + 1;
      fitnessSum += pseudoFitness[i];
      i++;
    }

    // create alias index
    int[] alias = new int[fitnessSum];
    
    // add the individual indexes a proportionate amount of times 
    int aliasIndex = 0;
    it = population.listIterator();
    for (int individual = 0; individual < population.size(); individual++) {
      for (int j = 0; j < pseudoFitness[individual]; j++) {
        alias[aliasIndex] = individual;
        aliasIndex++;
      }
    }
    
    Random rand = new Random(System.currentTimeMillis());
    
    while (children.size() < N) {
      if (alias.length == 0) {
        break;
      }

      int pi1 = alias[rand.nextInt(alias.length)];
      int numPi1 = pseudoFitness[pi1];
      int aIndex = rand.nextInt(alias.length - numPi1);

      int ai = 0;
      int j = 0;
      for (; j < alias.length && ai < aIndex; j++) {
        // skip ahead if we are at the span of the first parent's index
        while (j < (alias.length - 1) && alias[j] == pi1) {
          j++; 
        }

        ai++;
      }

      int pi2 = alias[j];

      TimeTable t1 = population.getIndividual(pi1);
      TimeTable t2 = population.getIndividual(pi2);

      TimeTable child = crossoverWithPoint(t1, t2);
      mutate(child);
      repairTimeTable(child);
      fitness(child);

      children.addIndividual(child);
    }
    
    return children;
  }

  private Population selection(Population population, Population children) {
    // population is already sorted
    children.sortIndividuals(); 

    Population nextPopulation = new Population();

    ListIterator<TimeTable> itParents = population.listIterator();
    ListIterator<TimeTable> itChildren = children.listIterator();
    TimeTable nextParent = next(itParents);
    TimeTable nextChild = next(itChildren);

    while (nextPopulation.size() < MAX_POPULATION_SIZE) {
      if (nextChild != null) {
        if (nextChild.getFitness() > nextParent.getFitness()) {
          nextPopulation.addIndividual(nextChild);

          nextChild = next(itChildren);
        
        } else {
          nextPopulation.addIndividual(nextParent);

          nextParent = next(itParents);
        }
      
      } else {
          if (nextParent != null) {
            // add the rest from population
            nextPopulation.addIndividual(nextParent);
            nextParent = next(itParents);
        }
      }
    }

    return nextPopulation;
  }

  /////////////////////////////


  private TimeTable crossoverWithPoint(TimeTable t1, TimeTable t2) {
		TimeTable child = new TimeTable(kth.getNumRooms());

		int interval = kth.getNumRooms() * StudentGroupTimeTable.NUM_TIMESLOTS *
																			 StudentGroupTimeTable.NUM_DAYS;

		int point = new Random(System.currentTimeMillis()).nextInt(interval);

		StudentGroupTimeTable[] rtts1 = t1.getSgTimeTables();
		StudentGroupTimeTable[] rtts2 = t2.getSgTimeTables();

		int gene = 0;

		// iterate over the genes
		for (int i = 0; i < kth.getNumRooms(); i++) {
			StudentGroupTimeTable rtt = new StudentGroupTimeTable(rtts1[i].getStudentGroup());

			// for each available time
			for (int timeslot = 0; timeslot < StudentGroupTimeTable.NUM_TIMESLOTS;
																											timeslot++) {
				for (int day = 0; day < StudentGroupTimeTable.NUM_DAYS; day++) {
					int allele;

					if (gene < point) {
						allele = rtts1[i].getEvent(day, timeslot);
					} else {
						allele = rtts2[i].getEvent(day, timeslot);
					}

					rtt.setEvent(day, timeslot, allele);
					gene++;
				}
			}

			child.putSgTimeTable(i, rtt);
		}

    return child;
  }

  private void repairTimeTable(TimeTable tt) {
    HashMap<Integer, LinkedList<RoomDayTime>> locations = new HashMap<Integer,
                                                    LinkedList<RoomDayTime>>();

    LinkedList<RoomDayTime> unusedSlots = new LinkedList<RoomDayTime>();

    // initiate number of bookings to 0
    for (int eventID : kth.getEvents().keySet()) {
      locations.put(eventID, new LinkedList<RoomDayTime>());
    }

    StudentGroupTimeTable[] rtts = tt.getSgTimeTables();

    for (int i = 0; i < kth.getNumRooms(); i++) {
      StudentGroupTimeTable rtt = rtts[i];
      // for each available time
      for (int timeslot = 0; timeslot < StudentGroupTimeTable.NUM_TIMESLOTS;
                                                     timeslot++) {
        for (int day = 0; day < StudentGroupTimeTable.NUM_DAYS; day++) {
          int bookedEvent = rtt.getEvent(day, timeslot);
          if (bookedEvent == 0) {
            // add to usable slots
            unusedSlots.add(new RoomDayTime(i, day, timeslot));

          } else {
            // save the location
            locations.get(bookedEvent).add(new RoomDayTime(i, day, timeslot));
          }
        }
      }
    }

    List<Integer> unbookedEvents = new LinkedList<Integer>();

    for (int eventID : kth.getEvents().keySet()) {
      if (locations.get(eventID).size() == 0) {
        // this event is unbooked
        unbookedEvents.add(eventID);

      } else if (locations.get(eventID).size() > 1) {
        // this is event is booked more than once
        // randomly make those slots unused until only one remains
        LinkedList<RoomDayTime> slots = locations.get(eventID);
        Collections.shuffle(slots);

        while (slots.size() > 1) {
          RoomDayTime rdt = slots.removeFirst();

          // mark this slot as unused
          unusedSlots.add(rdt);
          rtts[rdt.room].setEvent(rdt.day, rdt.time, 0);
        }
      }
    }

    // now put each unbooked event in an unused slot
    Collections.shuffle(unusedSlots);
    for (int eventID : unbookedEvents) {
      RoomDayTime rdt = unusedSlots.removeFirst();
      rtts[rdt.room].setEvent(rdt.day, rdt.time, eventID);
    }
  }

  // wrapper class only used in repair function
  private class RoomDayTime {
    int room;
    int day;
    int time;

    RoomDayTime(int room, int day, int time) {
      this.room = room;
      this.day = day;
      this.time = time;
    }
  }

  //////////////////////////
  // MUTATION
  //////////////////////////

  private void mutate(TimeTable tt) {
    Random rand = new Random(System.currentTimeMillis());
    StudentGroupTimeTable[] rtts = tt.getSgTimeTables();

    for (int i = 0; i < kth.getStudentGroups().size(); i++) {
      StudentGroupTimeTable rtt = rtts[i];
      // for each available time
      for (int timeslot = 0; timeslot < StudentGroupTimeTable.NUM_TIMESLOTS;
                                                            timeslot++) {
        for (int day = 0; day < StudentGroupTimeTable.NUM_DAYS; day++) {
          if (rand.nextInt(1000) < MUTATION_PROBABILITY) {
            // mutate this gene
            int swapTargetDay = rand.nextInt(StudentGroupTimeTable.NUM_DAYS);
            int swapTargetTimeslot = rand.nextInt(StudentGroupTimeTable.NUM_TIMESLOTS);
            int swapTargetEventId = rtt.getEvent(swapTargetDay, swapTargetTimeslot);
            int swapSrcEventId = rtt.getEvent(day, timeslot);
            rtt.setEvent(swapTargetDay, swapTargetTimeslot, swapSrcEventId);
            rtt.setEvent(day, timeslot, swapTargetEventId);
          }
        }
      }
    }
  }

  //////////////////////////
  // FITNESS
  //////////////////////////

  private void fitness(TimeTable tt) {
    // set the fitness to this time table
    
    int lecturerDoubleBookings = lecturerDoubleBookings(tt);
    int extraOrMissingLectures = extraOrMissingLectures(tt);
    int multipleTeachersForSameSGCourse = multipleTeachersForSameStudentGroupCourse(tt);

    int numBreaches = 2 * lecturerDoubleBookings + 4 * extraOrMissingLectures
        + 12 * multipleTeachersForSameSGCourse;

    int fitness = -1 * numBreaches;
    tt.setFitness(fitness);
  }

  private int extraOrMissingLectures(TimeTable tt) {
    // Each student group should have a number of expected lessons for each course
    Map<Integer, Map<Integer, Integer>> expectedLectures = new HashMap<>();
    for (StudentGroup sg : kth.getStudentGroups().values()) {
      Map<Integer, Integer> sgLectures = new HashMap<>();
      for (Course course : sg.getCourses()) {
        sgLectures.put(course.getId(), course.getNumLectures());
      }
      expectedLectures.put(sg.getId(), sgLectures);
    }

    Map<Integer, Map<Integer, Integer>> actualLectures = new HashMap<>();
    for (StudentGroupTimeTable sgTimeTable : tt.getSgTimeTables()) {
      int sgId = sgTimeTable.getStudentGroup().getId();
      Map<Integer, Integer> sgActualLectures = new HashMap<>();
      for (int timeslot = 0; timeslot < StudentGroupTimeTable.NUM_TIMESLOTS; timeslot++) {
        for (int day = 0; day < StudentGroupTimeTable.NUM_DAYS; day++) {
          int eventId = sgTimeTable.getEvent(day, timeslot);
          Event event = kth.getEvent(eventId);
          if (event != null) {
            int courseId = event.getCourse().getId();
            if (!sgActualLectures.containsKey(courseId)) {
              sgActualLectures.put(courseId, 0);
            }
            sgActualLectures.put(courseId, sgActualLectures.get(courseId) + 1);
          }
        }
      }
      actualLectures.put(sgId, sgActualLectures);
    }
    int extraOrMissingLectures = 0;
    for (Integer sgId : expectedLectures.keySet()) {
      Map<Integer, Integer> expectedSgLessons = expectedLectures.get(sgId);
      Map<Integer, Integer> actualSgLessons = actualLectures.get(sgId);
      for (Integer courseId : expectedSgLessons.keySet()) {
        Integer expectedCourseLessons = expectedSgLessons.get(courseId);
        Integer actualCourseLessons = actualSgLessons.get(courseId) != null ? actualSgLessons.get(courseId) : 0;
        extraOrMissingLectures += Math.abs(expectedCourseLessons - actualCourseLessons);
      }
    }

    return extraOrMissingLectures;
  }


  private int multipleTeachersForSameStudentGroupCourse(TimeTable tt) {
    int multipleTeachersCount = 0;
    for (StudentGroupTimeTable sgTimeTable : tt.getSgTimeTables()) {
      HashMap<Course, Lecturer> courseLecturerMap = new HashMap<>();
      for (int timeslot = 0; timeslot < StudentGroupTimeTable.NUM_TIMESLOTS; timeslot++) {
        for (int day = 0; day < StudentGroupTimeTable.NUM_DAYS; day++) {
          int eventId = sgTimeTable.getEvent(day, timeslot);
          Event event = kth.getEvent(eventId);
          Course course = event.getCourse();
          Lecturer lecturer = courseLecturerMap.get(course);
          if (lecturer != null) {
            // Si el lecturer del evento es distinto al almacenado en el mapa, hay mas de uno para la misma materia
            if (event.getLecturer() != lecturer) {
              multipleTeachersCount += 1;
            }
          }
          else {
            // Si no esta el curso en el mapa, lo agregamos con el lecturer del evento
            courseLecturerMap.put(course, event.getLecturer());
          }
        }
      }
    }
    return multipleTeachersCount;
  }

  //////////////////////////
  // CONSTRAINTS
  //////////////////////////

  ///////////////////
  // Hard constraints, each function returns the number of constraint breaches
  ///////////////////

  // NOTE: Two of the hard constraints are solved by the chosen datastructure
  // Invalid timeslots may not be used
  // A room can not be double booked at a certain timeslot

  private int max(int a, int b, int c) {
    int max = a;

    if (b > max) {
      max = b;
    }

    if (c > max) {
      max = c;
    }

    return max;
  }

  // num times a lecturer is double booked
  // NOTE: lecturers are only booked to lectures
  // for the labs and classes, TAs are used and they are assumed to always
  // be available
  private int lecturerDoubleBookings(TimeTable tt) {
    int numBreaches = 0;

    StudentGroupTimeTable[] timeTables = tt.getSgTimeTables();

    for (Lecturer lecturer : kth.getLecturers().values()) {

      // for each time
      for (int timeslot = 0; timeslot < StudentGroupTimeTable.NUM_TIMESLOTS;
                                                           timeslot++) {

        for (int day = 0; day < StudentGroupTimeTable.NUM_DAYS; day++) {
          int numBookings = 0;

          for (StudentGroupTimeTable studentGroupTimeTable : timeTables) {
            int eventID = studentGroupTimeTable.getEvent(day, timeslot);

            // 0 is unbooked
            if (eventID != 0) {
              Event event = kth.getEvent(eventID);
              // only check lectures since lecturers are only
              // attached to lecture events
              if (event.getType() == Event.Type.LECTURE) {
                if (event.getLecturer().getId() == lecturer.getId()) {
                  numBookings++;
                }
              }
            }
          }

          // only one booking per time is allowed
          if (numBookings > 1) {

            // add all extra bookings to the number of constraint breaches
            numBreaches += numBookings - 1;
          }
        }
      }
    }

    return numBreaches;
  }



  public void setMutationProbability(int p) {
    MUTATION_PROBABILITY = p;
  }

  public void setCrossoverProbability(int p) {
    CROSSOVER_PROBABILITY = p;
  }

  public void setPopulationSize(int size) {
    MAX_POPULATION_SIZE = size;
  }

  public void setSelectionSize(int size) {
    SELECTION_SIZE = size;
  }

  public void setMutationType(int i) {
    mutationType = MUTATION_TYPE.values()[i];
  }

  public void setMutationType(String type) {
    mutationType = MUTATION_TYPE.valueOf(type);
  }

  public void setSelectionType(int i) {
    selectionType = SELECTION_TYPE.values()[i];
  }

  public void setSelectionType(String type) {
    selectionType = SELECTION_TYPE.valueOf(type);
  }

  // print the given time table in a readable format
  public void printTimeTable(TimeTable tt) {
    kth.printTimeTable(tt);
  }

  public void printConf() {
    System.out.println("Desired fitness: " + DESIRED_FITNESS);
    System.out.println("Population size: " + MAX_POPULATION_SIZE);
    System.out.println("Selection size: " + SELECTION_SIZE);
    System.out.println("Mutation type: " + mutationType);
    System.out.println("P(Mutation) = " + ((double)MUTATION_PROBABILITY / 1000.0d * 100) + "%");
    System.out.println("Selection type: " + selectionType);
    System.out.println("P(Crossover) = " + ((double)CROSSOVER_PROBABILITY / 1000.0d * 100) + "%");
  }
}
