/** 指法练习词库（原 st_zhifa“背单词指法练习”：小学英语 / 中考英语 / 高考英语，另加基本键位练习） */
export const typingLevels: { value: string; label: string }[] = [
  { value: 'KEYS', label: '基本键位' },
  { value: 'PRIMARY', label: '小学英语' },
  { value: 'MIDDLE', label: '中考英语' },
  { value: 'HIGH', label: '高考英语' }
]

const KEYS = `asdf jkl; fdsa ;lkj asdf jkl; aaa sss ddd fff jjj kkk lll ;;; fj dk sl a; gh hg fg jh
qwer uiop rewq poiu qwer tyui zxcv m,./ vcxz /.,m ty ui op qw er zx cv bn nm
sad lad dad fad ask all fall hall glass flask shall salad jail kill skill daddy`

const PRIMARY = `apple banana orange pear grape milk water bread egg rice cake juice tea
cat dog bird fish pig duck cow horse sheep tiger lion monkey panda rabbit
red blue green yellow black white pink purple brown orange
one two three four five six seven eight nine ten
book pen ruler bag desk chair door window school class teacher student friend
father mother brother sister family home room bed kitchen garden park zoo
head face eye ear nose mouth hand foot arm leg hair
run jump swim sing dance read write draw play eat drink sleep walk
big small long short tall happy sad good nice cold hot new old
spring summer autumn winter sunny rainy windy cloudy today morning night`

const MIDDLE = `ability absent accept accident achieve active advice afraid against agree
airport alive allow almost alone already always amazing ancient angry
answer anxious appear area arrive article attend attention average avoid
balance basic beauty become believe benefit borrow brave breath bridge
celebrate century challenge chance character choose citizen climate collect comfortable
communicate compare complete computer condition confident connect continue control culture
danger decide degree delicious depend describe design develop difference difficult
education effort electric encourage energy enjoy environment especially event experience
famous festival foreign forget future general government habit health history
imagine important improve include information interest invent language library medicine
memory message modern national natural necessary opinion pollution population practice
protect provide quality quickly realize receive recycle relationship report research
science scientist society solve success suggest support technology traditional valuable volunteer`

const HIGH = `abandon absolute abstract academic accelerate accommodate accompany accumulate accurate adequate
adjust administration advocate aggressive allocate alternative ambiguous analyze anticipate apparent
appreciate approach appropriate approximate arbitrary assess assume attitude authority awareness
breakthrough capacity circumstance coincidence collapse commitment compensate competent comprehensive concentrate
consequence considerable consistent constitute contemporary contribute controversial convenient cooperate corporate
criticism crucial curriculum declare dedicate deficiency deliberate demonstrate determine dimension
discipline distinguish distribute diverse dominate economic efficient eliminate emphasize enormous
equivalent essential evaluate eventually evidence exaggerate expand explicit exploit facilitate
fundamental generate guarantee hypothesis identical illustrate implement implication incentive inevitable
infrastructure inherent initiative innovation integrate intellectual interpret investigate legislation maintain
mechanism negotiate objective obligation obtain occupation opportunity participate perspective phenomenon
potential precise predominant preliminary priority proportion psychology qualify recommend reinforce
reluctant represent reputation resolve resource restrict sophisticated substantial sufficient sustainable
temporary tremendous ultimate undertake vulnerable widespread`

const parse = (s: string) => s.split(/\s+/).filter(Boolean)

export const typingWords: Record<string, string[]> = {
  KEYS: parse(KEYS),
  PRIMARY: parse(PRIMARY),
  MIDDLE: parse(MIDDLE),
  HIGH: parse(HIGH)
}
