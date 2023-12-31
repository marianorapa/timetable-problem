public class Event {

	public static enum Type { LECTURE, LESSON, LAB };

	private static int nextId = 1;
	
  private final Type type;
	private final int id;
  private final int eventGroupId;
//  private final int size;
  private final Lecturer lecturer;
	private final Course course;
//  private final StudentGroup studentGroup;

	public Event(Type t, Lecturer l, Course c, int eventGroupId) {
    this.type = t;
//    this.size = size;
    this.lecturer = l;
		this.course = c;
//    this.studentGroup = s;
    id = nextId++;
    this.eventGroupId = eventGroupId;
	}

 	public int getId() {
		return id;
	}

  public int getEventGroupId() {
    return eventGroupId;
  }

//  public int getSize() {
//    return size;
//  }

	public Course getCourse() {
		return course;
	}

  public Lecturer getLecturer() {
    return lecturer;
  }

	public Type getType() {
		return type;
	}
  
//  public StudentGroup getStudentGroup() {
//    return studentGroup;
//  }
  
	public static void resetId() {
	  nextId = 1;
	}
	
	public static Type generateType(int i) {
		switch (i) {
			case 0:
				return Type.LECTURE;
			case 1:
				return Type.LESSON;
			case 2:
				return Type.LAB;
			default:
				break;
		}
		return null;
	}

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		result.append("Id: ").append(id).append(" - ");
		result.append("Type: ").append(type).append(" - ");
		result.append("Group id: ").append(eventGroupId).append(" - ");
//		result.append("Size: ").append(size).append(" - ");
		if (lecturer != null) {
			result.append(lecturer).append(" - ");
		}
		if (course != null) {
			result.append(course).append(" - ");
		}
//		if (studentGroup != null) {
//			result.append(studentGroup);
//		}
		return result.toString();
	}
}
