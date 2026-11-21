import unittest
import xml.etree.ElementTree as ET
from check_settings import restore_choices

class RestoreSettingsTest(unittest.TestCase):
    def test_preserves_original_days_without_rewinding_latest_cache(self):
        before=ET.fromstring('<map><set name="selected_days"><string>Day of Magic</string></set><boolean name="monitor_enabled" value="true"/><boolean name="allow_metered" value="true"/><string name="sample">old</string></map>')
        current=ET.fromstring('<map><set name="selected_days"><string>Reconstruction Day</string></set><boolean name="monitor_enabled" value="false"/><boolean name="allow_metered" value="false"/><string name="sample">fresh</string><int name="poll_seconds" value="60"/></map>')
        result=restore_choices(current,before)
        nodes={n.get('name'):n for n in result}
        self.assertEqual([n.text for n in nodes['selected_days']],['Day of Magic'])
        self.assertEqual(nodes['monitor_enabled'].get('value'),'true')
        self.assertEqual(nodes['allow_metered'].get('value'),'true')
        self.assertEqual(nodes['sample'].text,'fresh')
        self.assertEqual(nodes['poll_seconds'].get('value'),'60')

    def test_absent_default_preferences_are_not_invented(self):
        before=ET.fromstring('<map><string name="sample">old</string></map>')
        current=ET.fromstring('<map><set name="selected_days"/><boolean name="monitor_enabled" value="false"/><boolean name="allow_metered" value="false"/><string name="sample">fresh</string></map>')
        result=restore_choices(current,before)
        self.assertEqual([(n.get('name'),n.text) for n in result],[('sample','fresh')])

    def test_does_not_mutate_captured_original_or_live_input(self):
        before=ET.fromstring('<map><set name="selected_days"><string>Day of Magic</string></set></map>')
        current=ET.fromstring('<map><string name="sample">fresh</string></map>')
        old_before,old_current=ET.tostring(before),ET.tostring(current)
        result=restore_choices(current,before);result.find('set/string').text='Changed'
        self.assertEqual(ET.tostring(before),old_before)
        self.assertEqual(ET.tostring(current),old_current)

if __name__=='__main__':unittest.main()
