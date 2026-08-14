import 'capture_flows_test.dart' as capture_flows;
import 'editor_flow_test.dart' as editor_flow;
import 'settings_and_output_flow_test.dart' as settings_and_output_flow;

/// Single entry point for the whole e2e suite.
///
/// `flutter test integration_test` launches the app once per file, and the
/// desktop runner cannot reliably reattach for the second launch ("Unable to
/// start the app on the device"). Running the suites from one entry point keeps
/// them to a single launch:
///
/// ```bash
/// flutter test integration_test/all_tests.dart -d linux
/// ```
void main() {
  capture_flows.main();
  editor_flow.main();
  settings_and_output_flow.main();
}
