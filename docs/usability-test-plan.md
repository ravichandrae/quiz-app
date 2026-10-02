# Usability test plan

Acceptance criterion: *the system passes usability testing with at least 10 students and 2 admins*
(requirement 10). This plan describes how to run that test. It has **not been run yet**: it needs
real students and teachers.

## Who

- **10 students** aged 12–15 from the target schools. Include some who rarely use smartphones,
  and at least 3 who will use the Android app on a low-cost phone (1 GB RAM if possible).
- **2 teachers** who will run quizzes, at least one without much computer experience.
- One facilitator and one note-taker per session. Get parents' and the school's consent first.

## Setup

- A test server loaded with: 3 groups, about 20 questions, and 2 quizzes (one with an overall time
  limit, one that shows answers).
- For students: a shared tablet with the website, and a phone with the app.
- Watch on a 3G connection as well (the Chrome developer tools can slow the network down).
- Have each participant think aloud. **Do not help** unless they are stuck for 2 minutes. Note
  where help was needed.

## Student tasks (about 20 minutes each)

| # | Task | Success means |
|---|---|---|
| S1 | Create an account | Registered without help in under 3 minutes |
| S2 | Log out and log back in | Logged in at the first or second try |
| S3 | Find the new quiz and start it | Reached question 1 without help |
| S4 | Answer all questions | Understood the timer; submitted answers (not just chose them) |
| S5 | Leave mid-quiz, then continue | Found **Continue** and carried on |
| S6 | Find your score and see which answers were wrong | Read the score and the right/wrong marks correctly |
| S7 | (App) Same as S3–S6 on the phone | Same as above |

Ask afterwards: *What was confusing? What would you change? Was the time enough?* Also ask a
1–5 smiley-face rating of "easy to use".

## Teacher tasks (about 40 minutes each)

| # | Task | Success means |
|---|---|---|
| T1 | Add 5 questions | Correct answers marked correctly; under 10 minutes |
| T2 | Create a quiz from them with a 10-minute limit | Saved with the questions in the intended order |
| T3 | Create a group and add 5 students | Done without help |
| T4 | Give the quiz to the group, due in 2 days | Assigned correctly |
| T5 | A student forgot their PIN: reset it | Student can log in with the new PIN |
| T6 | Find who has not finished, and one student's answers | Used Results and View correctly |
| T7 | Download the results for the quiz | Opened the CSV in a spreadsheet program |

Ask afterwards: *Would you use this with your class? What took too long?* Also ask the 10-question
System Usability Scale (SUS).

## Passing

- Each task is done **without help** by at least 8 of 10 students (S tasks), and by both teachers
  (T tasks).
- No participant loses quiz answers or sees someone else's data.
- Median student rating ≥ 4 of 5; teachers' SUS score ≥ 70.

Record every problem with its severity (blocks the task / slows it / cosmetic). Fix every blocking
problem and retest it with at least 3 new students before signing off.
