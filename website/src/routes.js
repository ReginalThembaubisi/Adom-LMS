import { ADOM } from './lib/data.js';

// Every page on the site: its address, the component that renders it, and what search
// engines and browser tabs show for it.
const pages = [
  { path: '/', page: 'HomePage', title: 'Adom Technologies · Courses, learnerships, internships and placements in Nelspruit',
    description: 'Practical IT courses, learnerships, internships and university work placements in Nelspruit and online, from the team that builds software and fixes networks for local businesses.' },
  { path: '/about/', page: 'AboutPage', title: 'About Adom Technologies',
    description: 'Adom Technologies has built software and fixed tech for businesses in Mpumalanga since 2019, and teaches people to do the same.' },
  { path: '/services/', page: 'ServicesPage', title: 'IT services for business · Adom Technologies',
    description: 'Websites, mobile apps, cloud, networking and technical support for businesses around Nelspruit. Book an appointment online.' },
  { path: '/careers/', page: 'CareersPage', title: 'Careers, internships and jobs · Adom Technologies',
    description: 'Internships, graduate programmes and jobs at Adom Technologies and our partners. See the openings and apply online.' },
  { path: '/learnerships/', page: 'LearnershipsPage', title: 'Learnerships · Adom Technologies',
    description: 'Earn while you learn: SETA learnerships that combine classroom learning with real work and end in a nationally recognised qualification.' },
  { path: '/internships/', page: 'InternshipsPage', title: 'Internships · Adom Technologies',
    description: 'Get real work experience at Adom Technologies and our partner companies in software, IT support, design and business.' },
  { path: '/university-placements/', page: 'PlacementsPage', title: 'University work placements (WIL) · Adom Technologies',
    description: 'Final-year students at TUT, DUT and other universities: we find the workplace you need for your work-integrated learning so you can graduate.' },
  { path: '/courses/', page: 'CoursesPage', title: 'Courses · Adom Technologies',
    description: 'Twelve-month courses in programming, technology, design and business, on campus in Nelspruit or through e-Learning.' },
  { path: '/prospective-students/', page: 'ProspectivePage', title: 'Prospective students · Adom Technologies',
    description: 'Everything you need to choose a course, apply and get ready for your first day at Adom.' },
  { path: '/apply/', page: 'ApplyPage', title: 'Apply online · Adom Technologies',
    description: 'Apply for a course, learnership, internship or university placement at Adom Technologies in about 15 minutes.' },
  { path: '/status/', page: 'StatusPage', title: 'Check your application status · Adom Technologies',
    description: 'Check where your Adom application is with your reference number and ID number.' },
  { path: '/e-learning/', page: 'ELearningPage', title: 'e-Learning · Adom Technologies',
    description: 'Every Adom course is on our learning platform: notes, videos, assignments and tests, on your phone or laptop.' },
  ...ADOM.courses.map(c => ({
    path: '/courses/' + c.id + '/', page: 'CoursePage', props: { courseId: c.id },
    title: c.name + ' course · Adom Technologies', description: c.outcome + ' ' + c.summary
  }))
];

export default pages;
