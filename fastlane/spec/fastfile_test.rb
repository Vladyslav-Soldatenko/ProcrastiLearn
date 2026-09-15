require "minitest/autorun"

FASTLANE_LANES = {}

module UI
  def self.user_error!(message)
    raise message
  end

  def self.success(_message); end
end

def opt_out_usage; end

def default_platform(_platform); end

def platform(_platform)
  yield
end

def desc(_description); end

def lane(name, &block)
  FASTLANE_LANES[name] = block
end

load File.expand_path("../Fastfile", __dir__)

class FastfileTest < Minitest::Test
  MAIN = TOPLEVEL_BINDING.eval("self")

  def test_validate_metadata_calls_only_the_listing_upload
    uploaded = nil
    stub(:required_file!) { |_name| "key.json" }
    stub(:metadata_release_context!) { |_json_key| ["alpha", 17] }
    stub(:upload_to_play_store) { |**options| uploaded = options }

    FASTLANE_LANES.fetch(:validate_metadata).call

    assert_equal true, uploaded.fetch(:validate_only)
    assert_equal false, uploaded.fetch(:skip_upload_metadata)
    assert_equal false, uploaded.fetch(:skip_upload_images)
    assert_equal false, uploaded.fetch(:skip_upload_screenshots)
    assert_equal true, uploaded.fetch(:skip_upload_aab)
    assert_equal true, uploaded.fetch(:skip_upload_changelogs)
    assert_equal "alpha", uploaded.fetch(:track)
    assert_equal 17, uploaded.fetch(:version_code)
  end

  def test_production_lane_calls_only_the_prepared_draft_upload
    uploaded = nil
    release = {
      aab: "/tmp/prepared/aab/app.aab",
      metadata_path: "/tmp/prepared/metadata/android",
      version_code: 18,
      version_name: "1.4.4"
    }
    stub(:validate_confirmation!) { |_name, _expected, _operation| nil }
    stub(:ensure_clean_git_tree!) { |_operation| nil }
    stub(:required_file!) { |_name| "key.json" }
    stub(:prepared_release!) { release }
    stub(:active_play_version_codes) { |_json_key| [17] }
    stub(:upload_to_play_store) { |**options| uploaded = options }

    FASTLANE_LANES.fetch(:upload_production_draft).call

    assert_equal "draft", uploaded.fetch(:release_status)
    assert_equal "production", uploaded.fetch(:track)
    assert_equal false, uploaded.fetch(:skip_upload_aab)
    assert_equal false, uploaded.fetch(:skip_upload_changelogs)
    assert_equal true, uploaded.fetch(:skip_upload_metadata)
    assert_equal true, uploaded.fetch(:skip_upload_images)
    assert_equal true, uploaded.fetch(:skip_upload_screenshots)
    assert_equal release.fetch(:aab), uploaded.fetch(:aab)
    assert_equal release.fetch(:metadata_path), uploaded.fetch(:metadata_path)
  end

  private

  def stub(name, &implementation)
    MAIN.define_singleton_method(name, &implementation)
  end
end
