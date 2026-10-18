import unittest
from android_emulator_ready import startup_wait_button

class StartupDialogPolicy(unittest.TestCase):
    def xml(self, title, package='android'):
        return f'''<hierarchy><node package="{package}" text="{title}"/><node package="{package}" resource-id="android:id/aerr_wait" bounds="[10,20][110,60]"/></hierarchy>'''
    def test_recovers_only_os_startup(self):
        self.assertEqual((60,40), startup_wait_button(self.xml("System UI isn't responding")))
        self.assertEqual((60,40), startup_wait_button(self.xml("Launcher3 isn't responding")))
    def test_never_dismisses_product_anr(self):
        self.assertIsNone(startup_wait_button(self.xml("Investment Radar isn't responding")))
        self.assertIsNone(startup_wait_button(self.xml("System UI isn't responding", 'com.investmentradar.app')))
    def test_healthy_launcher_needs_no_recovery(self):
        self.assertIsNone(startup_wait_button('<hierarchy><node package="com.android.launcher3" text="Gallery"/></hierarchy>'))

if __name__ == '__main__': unittest.main()
