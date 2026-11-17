import pt.amigos.core.*;
public class ScheduleTest { public static void main(String[] a){
  int f=0;
  if(Days.of(1970,1,1)!=0) f++;
  if(Days.of(2000,3,1)-Days.of(2000,2,28)!=2) f++;
  if(Days.of(2026,3,1)-Days.of(2026,2,28)!=1) f++;
  Friend p=new Friend(); p.bDay=29; p.bMonth=2; p.bYear=2000;
  long t=Days.of(2026,2,27);
  if(Schedule.daysToBirthday(p,2026,t)!=1) f++;            // 28/02 em ano comum
  if(Schedule.turning(p,2026,t)!=26) f++;
  t=Days.of(2026,3,1);
  if(Schedule.daysToBirthday(p,2026,t)!=Days.of(2027,2,28)-t) f++;
  if(Schedule.turning(p,2026,t)!=27) f++;
  p.bDay=1; p.bMonth=3; if(Schedule.daysToBirthday(p,2026,t)!=0) f++;
  Friend n=new Friend(); if(Schedule.daysToBirthday(n,2026,t)!=-1) f++;
  n.everyDays=30; n.lastContact=1;
  if(Schedule.overdue(n,100,50,130)!=0) f++;
  if(Schedule.overdue(n,100,50,140)!=10) f++;
  n.lastContact=0; if(Schedule.overdue(n,100,50,90)!=10) f++;
  n.everyDays=0; if(Schedule.overdue(n,100,50,500)!=Long.MIN_VALUE) f++;
  int[] b=Schedule.parseBirthday("5/7/88"); if(b==null||b[0]!=5||b[1]!=7||b[2]!=1988) f++;
  if(Schedule.parseBirthday("31/4")!=null) f++;
  b=Schedule.parseBirthday("29-02"); if(b==null||b[2]!=0) f++;
  if(Schedule.parseBirthday("29/02/2023")!=null) f++;
  if(!Schedule.ago(1).equals("ontem")||!Schedule.ago(21).equals("há 3 semanas")) f++;
  Friend q=new Friend(); q.name="  ana maria silva "; if(!q.initials().equals("AS")) f++;
  System.out.println(f==0?"ALL PASS":"FAIL "+f);
}}
