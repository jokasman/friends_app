import pt.relacoes.core.*; import java.util.*;
public class CoreTest { static int f=0; static void ok(boolean b,String m){ if(!b){f++;System.out.println("FALHOU: "+m);} }
public static void main(String[] a){
  TimeZone.setDefault(TimeZone.getTimeZone("Europe/Lisbon"));
  for(long d=-1000;d<30000;d+=37){int[] y=Recur.ymd(d); ok(Days.of(y[0],y[1],y[2])==d,"ymd "+d);}
  Person p=new Person(); p.bDay=29; p.bMonth=2; p.bYear=2000;
  long t=Days.of(2026,2,20);
  ok(Recur.nextBirthday(p,t)==Days.of(2026,2,28),"29/02 em ano comum");
  long due=Recur.birthdayDue(p,7,t,9*60); ok(Days.of(due)==Days.of(2026,2,21),"7 dias antes");
  due=Recur.birthdayDue(p,7,Days.of(2026,2,22),9*60); ok(Days.of(due)==Days.of(2027,2,21),"passa para o ano seguinte "+Arrays.toString(Recur.ymd(Days.of(due))));
  ok(Days.of(Recur.birthdayDue(p,0,Days.of(2028,2,29),540))==Days.of(2028,2,29),"29/02 em ano bissexto");
  Calendar c=Calendar.getInstance(); c.setTimeInMillis(Recur.at(Days.of(2026,3,29),9*60)); ok(c.get(Calendar.HOUR_OF_DAY)==9&&c.get(Calendar.DAY_OF_MONTH)==29,"hora no dia da mudança de hora");
  Reminder r=new Reminder(); r.kind=Reminder.REPEAT; r.repeatUnit=Reminder.MONTH; r.repeatN=1; r.dueAt=Recur.at(Days.of(2026,1,31),600);
  long n=Recur.nextRepeat(r,Recur.at(Days.of(2026,1,31),601)); ok(Days.of(n)==Days.of(2026,2,28),"31/01 + 1 mês");
  n=Recur.nextRepeat(r,Recur.at(Days.of(2026,5,10),0)); ok(Days.of(n)>Days.of(2026,5,10),"recorrente fica no futuro");
  long q=Recur.outsideQuiet(Recur.at(Days.of(2026,5,10),23*60),22*60,9*60); ok(Days.of(q)==Days.of(2026,5,11)&&Fmt.time(q).equals("09:00"),"silêncio à noite");
  q=Recur.outsideQuiet(Recur.at(Days.of(2026,5,10),3*60),22*60,9*60); ok(Days.of(q)==Days.of(2026,5,10)&&Fmt.time(q).equals("09:00"),"silêncio de madrugada");
  q=Recur.outsideQuiet(Recur.at(Days.of(2026,5,10),12*60),22*60,9*60); ok(Fmt.time(q).equals("12:00"),"fora do silêncio");
  Person k=new Person(); k.everyDays=30; k.lastAt=1;
  ok(Health.of(k,100,50,110)==Health.GREEN,"verde"); ok(Health.of(k,100,50,123)==Health.YELLOW,"amarelo"); ok(Health.of(k,100,50,130)==Health.RED,"vermelho");
  k.lastAt=0; ok(Health.of(k,100,50,80)==Health.RED,"sem contacto conta desde a criação");
  k.everyDays=0; ok(Health.of(k,100,50,500)==Health.NONE,"sem frequência");
  ok(Goals.week(Days.of(2026,11,16))==Goals.week(Days.of(2026,11,22)),"segunda a domingo"); ok(Goals.week(Days.of(2026,11,22))!=Goals.week(Days.of(2026,11,23)),"nova semana à segunda");
  ok(Goals.weekStart(Goals.week(Days.of(2026,11,19)))==Days.of(2026,11,16),"início da semana");
  long today=Days.of(2026,11,19); List<long[]> cs=new ArrayList<>();
  for(int w=1;w<=3;w++) for(int i=0;i<3;i++) cs.add(new long[]{i,today-7*w});
  cs.add(new long[]{1,today-7*4});
  Map<Long,Integer> pw=Goals.perWeek(cs); ok(Goals.streak(pw,today,3)==3,"série 3 semanas: "+Goals.streak(pw,today,3));
  cs.add(new long[]{5,today}); cs.add(new long[]{6,today}); cs.add(new long[]{7,today}); ok(Goals.streak(Goals.perWeek(cs),today,3)==4,"semana atual cumprida");
  Person m=new Person(); m.name="Ana Maria Silva"; ok(Template.fill("Parabéns, {primeiro_nome}! {idade} anos",m,30).equals("Parabéns, Ana! 30 anos"),"variáveis");
  ok(Template.fill("Parabéns pelos {idade} anos, {nome}!",m,0).contains("Ana Maria Silva"),"nome completo");
  for(Object[] b:Template.BUILTIN) ok(((String)b[2]).length()>10,"modelo");
  System.out.println(f==0?"ALL PASS":"FAIL "+f);
}}
