import unittest
import json
from app import app
from database import init_db, get_db

class TestKalviTransportBackend(unittest.TestCase):
    def setUp(self):
        self.app = app.test_client()
        self.app.testing = True
        conn = get_db()
        conn.execute("UPDATE buses SET status = 'active'")
        conn.commit()
        conn.close()

    def test_01_dashboard_stats(self):
        res = self.app.get('/api/dashboard/stats')
        self.assertEqual(res.status_code, 200)
        data = res.get_json()
        self.assertGreaterEqual(data['total_students'], 40)
        self.assertEqual(data['total_stops'], 30)
        self.assertEqual(data['total_buses'], 3)
        self.assertEqual(data['total_capacity'], 42)
        print("[PASS] Dashboard stats verified: 40+ students, 30 Erode stops (10 per bus), 3 buses, 42 capacity")

    def test_02_students_list_and_search(self):
        res = self.app.get('/api/students')
        self.assertEqual(res.status_code, 200)
        students = res.get_json()
        self.assertGreaterEqual(len(students), 40)
        self.assertTrue(any(s['student_id'].startswith('25CSR') for s in students))
        print(f"[PASS] Student directory verified: {len(students)} students loaded with department roll numbers (e.g. 25CSR201)")

        # Test search by Roll No (e.g. 25CSR201)
        search_res = self.app.get('/api/students?roll_no=25CSR201')
        self.assertEqual(search_res.status_code, 200)
        search_data = search_res.get_json()
        self.assertGreaterEqual(len(search_data), 1)
        self.assertEqual(search_data[0]['student_id'], '25CSR201')
        print(f"[PASS] Roll Number search bar verified: located student {search_data[0]['name']} via 25CSR201")

    def test_03_admin_and_student_authentication(self):
        # 1. Admin login with User ID: kalvi@transport and Password: kalvi123
        admin_res = self.app.post('/api/auth/login',
                                  data=json.dumps({"role": "admin", "username": "kalvi@transport", "password": "kalvi123"}),
                                  content_type='application/json')
        self.assertEqual(admin_res.status_code, 200)
        self.assertTrue(admin_res.get_json()['success'])
        print("[PASS] Admin authentication verified with User ID: kalvi@transport and Password: kalvi123")

        # Invalid admin credentials rejected
        bad_admin = self.app.post('/api/auth/login',
                                  data=json.dumps({"role": "admin", "username": "admin", "password": "wrong"}),
                                  content_type='application/json')
        self.assertEqual(bad_admin.status_code, 401)

        # 2. Student login with User ID as Roll No (25CSR201) and Password (their roll no @ bus no)
        st_res = self.app.post('/api/auth/login',
                               data=json.dumps({"role": "student", "student_id": "25CSR201", "password": "25CSR201@B01"}),
                               content_type='application/json')
        self.assertEqual(st_res.status_code, 200)
        self.assertTrue(st_res.get_json()['success'])
        print("[PASS] Student authentication verified with Roll No 25CSR201 and Password 25CSR201@B01")

    def test_04_forgot_password_recovery(self):
        # Admin forgot password
        admin_forgot = self.app.post('/api/auth/forgot-password',
                                     data=json.dumps({"role": "admin", "verify_code": "kalvi"}),
                                     content_type='application/json')
        self.assertEqual(admin_forgot.status_code, 200)
        self.assertEqual(admin_forgot.get_json()['username'], 'kalvi@transport')
        self.assertEqual(admin_forgot.get_json()['password'], 'kalvi123')
        print("[PASS] Admin Forgot Password verified: recovered kalvi@transport / kalvi123")

        # Student forgot password
        st_forgot = self.app.post('/api/auth/forgot-password',
                                  data=json.dumps({"role": "student", "roll_no": "25CSR201"}),
                                  content_type='application/json')
        self.assertEqual(st_forgot.status_code, 200)
        self.assertEqual(st_forgot.get_json()['password'], '25CSR201@B01')
        print("[PASS] Student Forgot Password verified: recovered 25CSR201@B01")

    def test_05_admin_student_registration_module(self):
        # Admin registers new student with Roll No 25CSR265 for computer science
        conn = get_db()
        conn.execute("DELETE FROM fee_records WHERE student_id = '25CSR265'")
        conn.execute("DELETE FROM students WHERE student_id = '25CSR265'")
        conn.commit()
        conn.close()

        reg_payload = {
            "name": "Karthi Velusamy",
            "department": "Computer Science (CSE)",
            "roll_no": "25CSR265",
            "bus_id": "B01",
            "stop_id": "Erode Bus Stand",
            "semester": "6th Sem",
            "fee_paid": 0
        }
        reg_res = self.app.post('/api/admin/register-student',
                                data=json.dumps(reg_payload),
                                content_type='application/json')
        self.assertEqual(reg_res.status_code, 200)
        reg_data = reg_res.get_json()
        self.assertTrue(reg_data['success'])
        self.assertEqual(reg_data['student']['password'], '25CSR265@B01')
        print(f"[PASS] Student Registration module verified: created student 25CSR265 with password {reg_data['student']['password']}")

        # Verify new registered student can login
        login_res = self.app.post('/api/auth/login',
                                  data=json.dumps({"role": "student", "student_id": "25CSR265", "password": "25CSR265@B01"}),
                                  content_type='application/json')
        self.assertEqual(login_res.status_code, 200)
        self.assertTrue(login_res.get_json()['success'])
        print("[PASS] New registered student successfully logged in with Roll No: 25CSR265 and Password: 25CSR265@B01")

    def test_06_route_planning(self):
        res = self.app.post('/api/routes/plan')
        self.assertEqual(res.status_code, 200)
        data = res.get_json()
        self.assertTrue(data['success'])
        plan = data['plan']
        self.assertGreater(plan['total_boarded_count'], 0)
        self.assertEqual(len(plan['bus_plans']), 3)
        print(f"[PASS] Dijkstra route planning verified across Erode stops: {plan['total_boarded_count']} students boarded across {len(plan['bus_plans'])} buses")

    def test_07_student_fee_payment_lifecycle(self):
        target_roll = "25CSR204"
        conn = get_db()
        conn.execute("UPDATE fee_records SET paid = 0, transaction_id = NULL, receipt_no = NULL WHERE student_id = ?", (target_roll,))
        conn.execute("UPDATE students SET authorized = 0 WHERE student_id = ?", (target_roll,))
        conn.commit()
        conn.close()

        # 1. Fetch portal before payment
        res_before = self.app.get(f'/api/student/{target_roll}/portal')
        self.assertEqual(res_before.status_code, 200)
        st_before = res_before.get_json()
        self.assertEqual(st_before['fee'].get('paid'), 0)
        self.assertEqual(st_before['student'].get('authorized'), 0)

        # 2. Process online payment
        pay_res = self.app.post(f'/api/student/{target_roll}/pay-fee', 
                                data=json.dumps({"payment_method": "UPI (Google Pay)"}),
                                content_type='application/json')
        self.assertEqual(pay_res.status_code, 200)
        pay_data = pay_res.get_json()
        self.assertTrue(pay_data['success'])
        self.assertEqual(pay_data['fee']['paid'], 1)
        self.assertEqual(pay_data['student']['authorized'], 1)
        self.assertIn('RCPT-2026', pay_data['receipt']['receipt_no'])
        print(f"[PASS] Fee payment processed for Roll No {target_roll}: Txn={pay_data['receipt']['transaction_id']}")

    def test_08_bus_breakdown_and_restore(self):
        res = self.app.post('/api/buses/B01/breakdown')
        self.assertEqual(res.status_code, 200)
        data = res.get_json()
        self.assertTrue(data['success'])
        self.assertGreaterEqual(len(data['available_replacement_buses']), 1)
        print(f"[PASS] Bus breakdown handled: {len(data['available_replacement_buses'])} replacement buses located")

        res_restore = self.app.post('/api/buses/B01/restore')
        self.assertEqual(res_restore.status_code, 200)
        print("[PASS] Bus B01 restored to active")

    def test_09_student_self_attendance(self):
        target_roll = "25CSR204"
        res = self.app.post(f'/api/students/{target_roll}/attendance',
                            data=json.dumps({"present": 0}),
                            content_type='application/json')
        self.assertEqual(res.status_code, 200)
        st_res = self.app.get(f'/api/student/{target_roll}/portal')
        self.assertEqual(st_res.get_json()['student']['present'], 0)
        print(f"[PASS] Student {target_roll} self-attendance marked Absent")

        res2 = self.app.post(f'/api/students/{target_roll}/attendance',
                             data=json.dumps({"present": 1}),
                             content_type='application/json')
        self.assertEqual(res2.status_code, 200)
        st_res2 = self.app.get(f'/api/student/{target_roll}/portal')
        self.assertEqual(st_res2.get_json()['student']['present'], 1)
        print(f"[PASS] Student {target_roll} self-attendance restored to Present")

    def test_10_admin_cannot_alter_fee_policy(self):
        res = self.app.post('/api/fees/25CSR204/toggle')
        self.assertEqual(res.status_code, 403)
        self.assertFalse(res.get_json()['success'])
        print("[PASS] Admin fee tampering rejected (403): Fee must be paid via student portal")

if __name__ == '__main__':
    unittest.main()
