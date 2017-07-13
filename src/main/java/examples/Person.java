package examples;

import java.io.Serializable;

public class Person implements Serializable {
  private String name;
  private String email;
  private int revenue;
  private String address;

  public Person() {}

  public Person(String name, String email, String address) {
    this.name = name;
    this.email = email;
    this.address = address;
  }
  
  public Person(int idx) {
    this.name = createName(idx);
    this.email = "tzhou"+idx+"@example.com";
    this.address = ""+idx+" Lindon St, Portland_OR_"+(97000+idx);
    this.revenue = idx*1000;
  }

  static String createName(int idx) {
    return "Tom"+idx+" Zhou";
  }
  
  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getAddress() {
    return address;
  }

  @Override
  public String toString() {
    final StringBuffer sb = new StringBuffer("Person{");
    sb.append("name='").append(name).append('\'');
    sb.append(", email='").append(email).append('\'');
    sb.append(", address='").append(address).append('\'');
    sb.append(", revenue='").append(revenue).append('\'');
    sb.append('}');
    return sb.toString();
  }
}
