# Runs the publish lane with a stubbed Play uploader; never contacts Google Play.
require "tmpdir"

module UI
  def self.user_error!(message)
    raise ArgumentError, message
  end
end

def default_platform(_name); end

def platform(_name)
  yield
end

def desc(_description); end

def lane(name, &block)
  raise "Unexpected lane" unless name == :publish
  $publish_lane = block
end

def upload_to_play_store(**options)
  $upload_options = options
end

load File.join(__dir__, "Fastfile")

def assert(condition, message)
  raise message unless condition
end

keys = %w[AAB_PATH PLAY_JSON_KEY_PATH GOOGLE_APPLICATION_CREDENTIALS PLAY_TRACK PLAY_RELEASE_STATUS PACKAGE_NAME PLAY_UPLOAD_METADATA PLAY_METADATA_PATH]
previous = keys.to_h { |key| [key, ENV[key]] }
checks = 0
begin
  Dir.mktmpdir("scenedeck-fastlane-") do |directory|
    aab = File.join(directory, "app-release.aab")
    json = File.join(directory, "play.json")
    File.write(aab, "fixture bundle")
    File.write(json, '{"type":"service_account"}')
    keys.each { |key| ENV.delete(key) }
    ENV["AAB_PATH"] = aab
    ENV["PLAY_JSON_KEY_PATH"] = json
    $publish_lane.call
    assert($upload_options[:package_name] == "com.worxbend.scenedeck", "Wrong default package")
    assert($upload_options[:track] == "internal", "Wrong default track")
    assert($upload_options[:release_status] == "draft", "Wrong default status")
    checks += 1

    %w[internal beta production].product(%w[draft completed]).each do |track, status|
      ENV["PLAY_TRACK"] = track
      ENV["PLAY_RELEASE_STATUS"] = status
      $publish_lane.call
      assert($upload_options[:track] == track && $upload_options[:release_status] == status, "Wrong destination")
      assert($upload_options[:aab] == aab && $upload_options[:json_key] == json, "Wrong inputs")
      %i[skip_upload_apk skip_upload_metadata skip_upload_changelogs skip_upload_images skip_upload_screenshots].each do |key|
        assert($upload_options[key] == true, "Unexpected additional upload")
      end
      checks += 1
    end

    {
      "PLAY_TRACK" => "unsupported",
      "PLAY_RELEASE_STATUS" => "inProgress",
      "PLAY_UPLOAD_METADATA" => "yes",
      "PACKAGE_NAME" => "invalid-package",
      "AAB_PATH" => File.join(directory, "missing.aab"),
      "PLAY_JSON_KEY_PATH" => File.join(directory, "missing.json")
    }.each do |key, invalid|
      original = ENV[key]
      ENV[key] = invalid
      begin
        $publish_lane.call
        raise "Invalid #{key} reached uploader"
      rescue ArgumentError
        checks += 1
      ensure
        ENV[key] = original
      end
    end

    # OIDC credentials produced by google-github-actions/auth use external_account.
    File.write(json, '{"type":"external_account"}')
    ENV.delete("PLAY_JSON_KEY_PATH")
    ENV["GOOGLE_APPLICATION_CREDENTIALS"] = json
    $publish_lane.call
    assert($upload_options[:json_key] == json, "OIDC credentials were not forwarded")
    checks += 1

    ENV["PLAY_UPLOAD_METADATA"] = "true"
    ENV["PLAY_METADATA_PATH"] = File.join(directory, "metadata")
    begin
      $publish_lane.call
      raise "Missing listing reached uploader"
    rescue ArgumentError
      checks += 1
    end
    locale = File.join(ENV["PLAY_METADATA_PATH"], "en-US")
    Dir.mkdir(ENV["PLAY_METADATA_PATH"])
    Dir.mkdir(locale)
    File.write(File.join(locale, "title.txt"), "SceneDeck")
    $publish_lane.call
    assert($upload_options[:metadata_path] == ENV["PLAY_METADATA_PATH"], "Wrong listing path")
    %i[skip_upload_metadata skip_upload_changelogs skip_upload_images skip_upload_screenshots].each do |key|
      assert($upload_options[key] == false, "Listing upload was skipped")
    end
    checks += 1
    ENV["PLAY_UPLOAD_METADATA"] = "false"

    File.write(json, '{"type":"authorized_user"}')
    begin
      $publish_lane.call
      raise "Unsupported credential type reached uploader"
    rescue ArgumentError
      checks += 1
    end

    File.write(json, "sensitive-invalid-fixture")
    begin
      $publish_lane.call
      raise "Malformed credentials reached uploader"
    rescue ArgumentError => error
      assert(!error.message.include?("sensitive-invalid-fixture"), "Credential content leaked")
      checks += 1
    end
  end
ensure
  previous.each { |key, value| value.nil? ? ENV.delete(key) : ENV[key] = value }
end
puts "#{checks} publish-lane checks passed (Google Play calls stubbed)."
