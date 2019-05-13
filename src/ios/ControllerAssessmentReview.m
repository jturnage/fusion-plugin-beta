
#import "AssessmentResult.h"
#import "AssessmentPlugin.h"
#import "ControllerAssessmentReview.h"
#import "MaterialActivityIndicator.h"

@implementation ControllerAssessmentReview {
  UIAlertController* alertController;
  MDCActivityIndicator* waitIndicator;
  UILabel* waitLabel;
  UIView* waitCover;
  UIView* waitDescription;
  UIView* decisionModal;
  NSTimer* loadingTimer;
  BOOL initializedSeekbar;
  BOOL hasExercisesRemaining;
}

-(id) initWithNibName:(NSString *)nibNameOrNil bundle:(NSBundle *)nibBundleOrNil {
  self = [super initWithNibName:nibNameOrNil bundle:nibBundleOrNil];
  return self;
}

-(void) initSeekbar {
  AVPlayer* player = self.moviePlayer.player;
  AVPlayerItem* playerItem = [player currentItem];
  CMTime duration = [playerItem.asset duration];
  if (CMTIME_IS_INVALID(duration)) {
      return;
  }
  
  double seconds = CMTimeGetSeconds(duration);
  if (isfinite(seconds)) {
    initializedSeekbar = YES;
    __weak id weakSelf = self;

    [self.slider setHidden:YES];
    [self.playbackButton setSelected:NO];
    [self.playbackButton setHidden:NO];
    
    CGFloat seekbarWidth = CGRectGetWidth([self.slider bounds]);
    double interval = (0.5 * (seconds / seekbarWidth));

    CMTime seekbarSeconds = CMTimeMakeWithSeconds(interval, NSEC_PER_SEC);
    seekbarObserver = [player addPeriodicTimeObserverForInterval:seekbarSeconds queue:dispatch_get_main_queue() usingBlock:^(CMTime time) {
      [weakSelf seekbarSync];
    }];
  }
}

-(IBAction) cancel:(id)sender forEvent:(UIEvent *)event {
  alertController = [UIAlertController alertControllerWithTitle:@"Warning" message:@"You are about to leave this section of the test and will lose any data." preferredStyle:UIAlertControllerStyleAlert];
  [alertController addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault handler:^(UIAlertAction* action) {
    [self.plugin cancelled];
  }]];
  [alertController addAction:[UIAlertAction actionWithTitle:@"Cancel" style:UIAlertActionStyleDefault handler:nil]];
  
  [self presentViewController:alertController animated:YES completion:nil];
}

-(IBAction) retakeVideo:(id)sender forEvent:(UIEvent *)event {
  ControllerAssessmentOverlay* parent = (ControllerAssessmentOverlay*)self.parentViewController;
  [[parent overlayImage] setHidden:NO];
  [[parent timerLabel] setText:@"00.000"];
  [parent retakeVideo:self forMovie:[self.plugin currentVideoUrl]];
}

-(IBAction) saveVideo:(id)sender forEvent:(UIEvent *)event {
  [self ensureWaitCover];
  [self ensureWaitIndicator];
  [self ensureWaitDescription:@"We are saving your video. This may take a moment based on your connection."];

  [self uploadVideo];
}

-(IBAction) togglePlayback:(id)sender forEvent:(UIEvent *)event {
  [self.saveInfoView setHidden:YES];
  [self.saveButton setHidden:YES];
  [self.takeButton setHidden:YES];
  
  if ([self.playbackButton isSelected]) {
    [self.saveInfoView setHidden:NO];
    [self.saveButton setHidden:NO];
    [self.moviePlayer.player pause];
  } else
    [self.moviePlayer.player play];

  [self.playbackButton setSelected:![self.playbackButton isSelected]];
}

-(IBAction) seekbarAction:(UISlider *)sender forEvent:(UIEvent *)event {
  [[self.saveInfoView layer] setHidden:YES];

  AVPlayer* player = self.moviePlayer.player;
  AVPlayerItem* playerItem = [player currentItem];
  CMTime duration = [playerItem.asset duration];

  double seconds = CMTimeGetSeconds(duration);
  float minValue = [sender minimumValue];
  float maxValue = [sender maximumValue];
  float currentValue = [sender value];
  double value = seconds * (currentValue - minValue) / (maxValue - minValue);
  
  CMTime time = CMTimeMakeWithSeconds(value, NSEC_PER_SEC);
  [player seekToTime:time toleranceBefore:kCMTimeZero toleranceAfter:kCMTimeZero];
}

-(IBAction) seekbarPause:(id)sender forEvent:(UIEvent *)event {
  AVPlayer* player = self.moviePlayer.player;
  [player setRate:0.f];

  [self.saveButton setHidden:NO];
  [self.playbackButton setSelected:NO];
}

-(void) uploadVideo {
  NSString* boundary = @"FfD04x";
  AssessmentExercise* exercise = [self.plugin exercise];

  if (![self.plugin uploadEndpointUrl]) {
    return;
  }
  
  dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_BACKGROUND, 0), ^(void) {
    NSData* file = [NSData dataWithContentsOfURL:[self.plugin currentVideoUrl]];
    NSData* payload = [self generateFileUploadData:boundary data:file parameters:@{
      @"filename": [NSString stringWithFormat:@"%@-video.mp4", [exercise filePrefix]],
      @"testId": [exercise testId],
      @"testTypeId": [exercise testTypeId],
      @"uniqueId": [exercise uniqueId],
      @"version": [exercise version],
      @"viewId": [exercise viewId],
      @"exerciseId": [exercise exerciseId],
      @"bodySideId": [exercise bodySideId],
      @"exerciseName": [exercise name]
    }];
    
    NSString* payloadLength = [NSString stringWithFormat:@"%lu", (unsigned long)[payload length]];
    NSMutableURLRequest* request = [[NSMutableURLRequest alloc] initWithURL:[self.plugin uploadEndpointUrl] cachePolicy:NSURLRequestReloadIgnoringLocalCacheData timeoutInterval:300];
    [request setHTTPMethod:@"POST"];
    if ([self.plugin apiVersion])
      [request setValue:[self.plugin apiVersion] forHTTPHeaderField:@"X-Api-Version"];
    if ([self.plugin apiAuthorize])
      [request setValue:[self.plugin apiAuthorize] forHTTPHeaderField:@"Authorization"];
    [request setValue:payloadLength forHTTPHeaderField:@"Content-Length"];
    [request setValue:[NSString stringWithFormat:@"multipart/form-data; boundary=%@", boundary] forHTTPHeaderField:@"Content-Type"];
    [request setHTTPBody:payload];
    
    NSURLSessionConfiguration* configuration = NSURLSessionConfiguration.defaultSessionConfiguration;
    NSURLSession* session = [NSURLSession sessionWithConfiguration:configuration delegate:self delegateQueue:NSOperationQueue.mainQueue];
    
    NSURLSessionTask* task = [session dataTaskWithRequest:request completionHandler:^(NSData* data, NSURLResponse* response, NSError* error) {
      if (error.code != noErr) {
        dispatch_async(dispatch_get_main_queue(), ^(void) {
          if (waitIndicator && waitIndicator.isAnimating)
            loadingTimer = [NSTimer scheduledTimerWithTimeInterval:1.15 target:self selector:@selector(uploadingFailedFired:) userInfo:nil repeats:NO];
          else
            [self uploadingFailedFired:nil];
        });
        return;
      }
      
      NSHTTPURLResponse* httpResponse = (NSHTTPURLResponse*)response;
      NSInteger statusCode = [httpResponse statusCode];
      if (statusCode >= 200 && statusCode < 300) {
        NSError* jsonError = nil;
        NSDictionary* json = [NSJSONSerialization JSONObjectWithData:data options:kNilOptions error:&jsonError];
        if (jsonError.code != noErr) {
          dispatch_async(dispatch_get_main_queue(), ^(void) {
            if (waitIndicator && waitIndicator.isAnimating)
              loadingTimer = [NSTimer scheduledTimerWithTimeInterval:1.15 target:self selector:@selector(uploadingFailedFired:) userInfo:nil repeats:NO];
            else
              [self uploadingFailedFired:nil];
          });
        }
        
        id haveAllVideos = json[@"HaveAllVideos"];
        hasExercisesRemaining = haveAllVideos ? ![haveAllVideos boolValue] : NO;
        
        dispatch_async(dispatch_get_main_queue(), ^(void) {
          if (waitIndicator && waitIndicator.isAnimating) {
            [waitIndicator setProgress:1.0];
            [waitLabel setText:@"100%"];
            loadingTimer = [NSTimer scheduledTimerWithTimeInterval:1.15 target:self selector:@selector(uploadingFinishFired:) userInfo:nil repeats:NO];
          } else
            [self uploadingFinishFired:nil];
        });
        return;
      }
      
      dispatch_async(dispatch_get_main_queue(), ^(void) {
        if (waitIndicator && waitIndicator.isAnimating)
          loadingTimer = [NSTimer scheduledTimerWithTimeInterval:1.15 target:self selector:@selector(uploadingFailedFired:) userInfo:nil repeats:NO];
        else
          [self uploadingFailedFired:nil];
      });
    }];
    [task resume];
  });
}

-(void) playerReachedEnd:(NSNotification *)notification {
  AVPlayerItem* item = [notification object];
  [item seekToTime:kCMTimeZero];

  [self.saveButton setHidden:NO];
  [self.playbackButton setSelected:NO];
}

-(void) observeValueForKeyPath:(NSString *)keyPath ofObject:(id)object change:(NSDictionary<NSKeyValueChangeKey,id> *)change context:(void *)context {
  AVPlayer* player = [self.moviePlayer player];
  AVPlayerItem* playerItem = [player currentItem];

  if (object == player && [keyPath isEqualToString:@"status"]) {
    if (player.status == AVPlayerStatusFailed) {
      alertController = [UIAlertController alertControllerWithTitle:@"Unable to Retrieve Video" message:@"The selected video has failed to load. Please try again later." preferredStyle:UIAlertControllerStyleAlert];
      [alertController addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault handler:^(UIAlertAction* action) {
        [self.plugin failed:@"Unable to retrieve video"];
      }]];
      
      [self presentViewController:alertController animated:YES completion:nil];
      return;
    }
  }

  if (object == playerItem && [keyPath isEqualToString:@"status"]) {
    if (playerItem.status == AVPlayerItemStatusFailed) {
      alertController = [UIAlertController alertControllerWithTitle:@"Unable to Retrieve Video" message:@"The selected video has failed to load. Please try again later." preferredStyle:UIAlertControllerStyleAlert];
      [alertController addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault handler:^(UIAlertAction* action) {
        [self.plugin failed:@"Unable to retrieve video"];
      }]];
      
      [self presentViewController:alertController animated:YES completion:nil];
      return;
    }
  }
}

-(void) URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didSendBodyData:(int64_t)bytesSent totalBytesSent:(int64_t)totalBytesSent totalBytesExpectedToSend:(int64_t)totalBytesExpectedToSend {
  dispatch_async(dispatch_get_main_queue(), ^(void) {
    if (waitIndicator && waitLabel) {
      float percentage = ((float)totalBytesSent/(float)totalBytesExpectedToSend);
      if (percentage > 0.98)
        percentage = 0.99;

      [waitIndicator setProgress:percentage];
      [waitLabel setText:[NSString stringWithFormat:@"%d%%", (int)(percentage * 100)]];
      
      if (!waitIndicator.isAnimating)
        [waitIndicator startAnimating];
    }
  });
}

-(void) seekbarSync {
  AVPlayer* player = [self.moviePlayer player];
  AVPlayerItem* playerItem = [player currentItem];
  CMTime duration = [playerItem.asset duration];
  CMTime currentTime = [player currentTime];
  
  if (CMTIME_IS_INVALID(duration)) {
    self.slider.minimumValue = 0.0;
    return;
  }
  
  double seconds = CMTimeGetSeconds(duration);
  double currentSeconds = CMTimeGetSeconds(currentTime);

  NSDate* currentDate = [[NSDate alloc] initWithTimeIntervalSince1970:currentSeconds];
  NSDateFormatter* formatter = [[NSDateFormatter alloc] init];
  [formatter setDateFormat:@"ss.SSS"];
  [formatter setTimeZone:[NSTimeZone timeZoneForSecondsFromGMT:0.0]];
  [self.timerLabel setText:[formatter stringFromDate:currentDate]];
  
  float minValue = [self.slider minimumValue];
  float maxValue = [self.slider maximumValue];
  [self.slider setValue:(maxValue - minValue) * currentSeconds/seconds + minValue];
}

-(void) ensureWaitCover {
  if (waitCover)
    return;

  waitCover = [[UIView alloc] initWithFrame:[[UIScreen mainScreen] bounds]];
  [waitCover setBackgroundColor:[[UIColor blackColor] colorWithAlphaComponent:0.65]];
  [self.view addSubview:waitCover];
}

-(void) ensureWaitIndicator {
  if (waitIndicator && waitLabel)
    return;

  CGRect bounds = self.view.bounds;
  CGSize size = bounds.size;
  CGPoint center = CGPointMake(size.width / 2, size.height / 3);

  waitIndicator = [[MDCActivityIndicator alloc] init];
  [waitIndicator setRadius:44.0];
  [waitIndicator setStrokeWidth:7.0];
  [waitIndicator setIndicatorMode:MDCActivityIndicatorModeDeterminate];
  [waitIndicator setCycleColors:@[]];
  [waitIndicator setCenter:center];

  waitLabel = [[UILabel alloc] initWithFrame:bounds];
  [waitLabel setFont:[UIFont systemFontOfSize:22.0 weight:UIFontWeightSemibold]];
  [waitLabel setTextColor:UIColor.whiteColor];
  [waitLabel setTextAlignment:NSTextAlignmentCenter];
  [waitLabel setCenter:center];

  [self ensureWaitCover];
  [waitCover addSubview:waitIndicator];
  [waitCover addSubview:waitLabel];
}

-(void) ensureWaitDescription:(NSString *)description {
  if (waitDescription)
    return;

  CGRect mainBounds = self.view.bounds;
  CGSize mainSize = mainBounds.size;
  
  waitDescription = [[UIView alloc] initWithFrame:CGRectMake(16, 24, mainSize.width - 32, 75)];
  [waitDescription setBackgroundColor:UIColor.lightGrayColor];
  [waitDescription setContentMode:UIViewContentModeScaleToFill];
  [waitDescription setAutoresizingMask:(
    UIViewAutoresizingFlexibleWidth |
    UIViewAutoresizingFlexibleLeftMargin |
    UIViewAutoresizingFlexibleRightMargin |
    UIViewAutoresizingFlexibleTopMargin)];
  [[waitDescription layer] setCornerRadius:8];

  CGRect viewBounds = waitDescription.bounds;
  CGSize viewSize = viewBounds.size;
  
  UILabel* label = [[UILabel alloc] initWithFrame:CGRectMake(10, 17, viewSize.width - 20, 40)];
  [label setNumberOfLines:2];
  [label setLineBreakMode:NSLineBreakByTruncatingTail];
  [label setTextAlignment:NSTextAlignmentCenter];
  [label setContentMode:UIViewContentModeTop];
  [label setBaselineAdjustment:UIBaselineAdjustmentAlignBaselines];
  [label setContentHuggingPriority:(UILayoutPriorityDefaultHigh + 1) forAxis:UILayoutConstraintAxisHorizontal];
  [label setContentHuggingPriority:(UILayoutPriorityDefaultHigh + 1) forAxis:UILayoutConstraintAxisVertical];
  [label setAdjustsFontSizeToFitWidth:YES];
  [label setMinimumScaleFactor:0.4];
  [label setFont:[UIFont systemFontOfSize:16.0]];
  [label setAutoresizingMask:(
    UIViewAutoresizingFlexibleWidth |
    UIViewAutoresizingFlexibleHeight)];
  [label setText:description];
  
  [self ensureWaitCover];
  [waitDescription addSubview:label];
  [waitCover addSubview:waitDescription];
}

-(void) ensureSuccessModal {
  CGRect mainBounds = [[UIScreen mainScreen] bounds];
  CGSize mainSize = mainBounds.size;

  CGRect modalBounds = CGRectMake(0, 0, mainSize.width - 50, 225.0);
  if (modalBounds.size.width > 350)
    modalBounds = CGRectMake(0, 0, 350, 225.0);

  CGSize modalSize = modalBounds.size;
  float modalWidth = modalSize.width;
  float modalHeight = modalSize.height;

  decisionModal = [[UIView alloc] initWithFrame:modalBounds];
  [decisionModal setCenter:CGPointMake(mainSize.width / 2, mainSize.height / 2)];
  [decisionModal setBackgroundColor:UIColor.whiteColor];
  [decisionModal setAutoresizingMask:(
    UIViewAutoresizingFlexibleWidth |
    UIViewAutoresizingFlexibleLeftMargin |
    UIViewAutoresizingFlexibleRightMargin |
    UIViewAutoresizingFlexibleTopMargin |
    UIViewAutoresizingFlexibleBottomMargin)];
  [[decisionModal layer] setCornerRadius:12];

  UIImage* image = [UIImage imageNamed:@"AssessmentPlugin.bundle/check-mark.png"];
  UIImageView* imageView = [[UIImageView alloc] initWithImage:image];
  imageView.frame = CGRectMake(0, 25, 40, 40);

  CGPoint imageViewCenter = imageView.center;
  imageViewCenter.x = (mainSize.width / 2) - 30;
  [imageView setCenter:imageViewCenter];

  UILabel* savedLabel = [[UILabel alloc] initWithFrame:CGRectMake(0, 75, modalWidth, 50)];
  [savedLabel setText:@"Video Saved"];
  [savedLabel setTextAlignment:NSTextAlignmentCenter];
  [savedLabel setFont:[UIFont preferredFontForTextStyle:UIFontTextStyleTitle1]];

  UIButton* nextButton = [UIButton buttonWithType:UIButtonTypeSystem];
  [nextButton addTarget:self action:@selector(continueToNextExercise) forControlEvents:UIControlEventTouchUpInside];
  [nextButton setFrame:CGRectMake(15, modalHeight - 80, modalWidth - 30, 60)];
  [nextButton setBackgroundColor:UIColorWithHexString(@"#41baec")];
  [nextButton setTitle:@"NEXT" forState:UIControlStateNormal];
  [nextButton setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
  [[nextButton titleLabel] setFont:[UIFont boldSystemFontOfSize:([nextButton titleLabel].font.pointSize - 1)]];
  [[nextButton layer] setCornerRadius:8];

  [self ensureWaitCover];
  [waitCover addSubview:decisionModal];
  [decisionModal addSubview:imageView];
  [decisionModal addSubview:savedLabel];
  [decisionModal addSubview:nextButton];
}

-(void) ensureDecisionModal {
  CGRect mainBounds = [[UIScreen mainScreen] bounds];
  CGSize mainSize = mainBounds.size;
  CGRect modalBounds = CGRectMake(0, 0, mainSize.width - 50, 325);
  if (modalBounds.size.width > 350)
    modalBounds = CGRectMake(0, 0, 350, 325);

  CGSize modalSize = modalBounds.size;
  float modalWidth = modalSize.width;
  float modalHeight = modalSize.height;

  decisionModal = [[UIView alloc] initWithFrame:modalBounds];
  [decisionModal setCenter:CGPointMake(mainSize.width / 2, mainSize.height / 2)];
  [decisionModal setBackgroundColor:UIColor.whiteColor];
  [decisionModal setAutoresizingMask:(
    UIViewAutoresizingFlexibleWidth |
    UIViewAutoresizingFlexibleLeftMargin |
    UIViewAutoresizingFlexibleRightMargin |
    UIViewAutoresizingFlexibleTopMargin |
    UIViewAutoresizingFlexibleBottomMargin)];
  [[decisionModal layer] setCornerRadius:12];

  UIImage* image = [UIImage imageNamed:@"AssessmentPlugin.bundle/cross-mark.png"];
  UIImageView* imageView = [[UIImageView alloc] initWithImage:image];
  imageView.frame = CGRectMake(0, 25, 40, 40);

  CGPoint imageViewCenter = imageView.center;
  imageViewCenter.x = (mainSize.width / 2) - 30;
  [imageView setCenter:imageViewCenter];

  UILabel* errorLabel = [[UILabel alloc] initWithFrame:CGRectMake(0, 75, modalWidth, 50)];
  [errorLabel setText:@"Saving Error"];
  [errorLabel setTextAlignment:NSTextAlignmentCenter];
  [errorLabel setFont:[UIFont preferredFontForTextStyle:UIFontTextStyleTitle1]];
  
  UILabel* orLabel = [[UILabel alloc] initWithFrame:CGRectMake((modalWidth/2 - 20), modalHeight - 120, 40, 40)];
  [orLabel setText:@"OR"];
  [orLabel setTextColor:UIColorWithHexString(@"#dfdfdf")];
  [orLabel setFont:[UIFont systemFontOfSize:(orLabel.font.pointSize - 2)]];
  [orLabel setTextAlignment:NSTextAlignmentCenter];
  [orLabel setBackgroundColor:UIColor.whiteColor];
  
  UIView* orLine = [[UIView alloc] initWithFrame:CGRectMake(15, modalHeight - 100, modalWidth - 30, 2)];
  [orLine setBackgroundColor:UIColorWithHexString(@"#dfdfdf")];
  
  UIButton* recordButton = [UIButton buttonWithType:UIButtonTypeSystem];
  [recordButton addTarget:self action:@selector(continueToRecordNew) forControlEvents:UIControlEventTouchUpInside];
  [recordButton setFrame:CGRectMake(15, modalHeight - 180, modalWidth - 30, 60)];
  [recordButton setBackgroundColor:UIColorWithHexString(@"#00b96d")];
  [recordButton setTitle:@"RECORD NEW VIDEO" forState:UIControlStateNormal];
  [recordButton setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
  [[recordButton titleLabel] setFont:[UIFont boldSystemFontOfSize:([recordButton titleLabel].font.pointSize - 1)]];
  [[recordButton layer] setCornerRadius:8];
  
  UIButton* againButton = [UIButton buttonWithType:UIButtonTypeSystem];
  [againButton addTarget:self action:@selector(continueToTryAgain) forControlEvents:UIControlEventTouchUpInside];
  [againButton setFrame:CGRectMake(15, modalHeight - 80, modalWidth - 30, 60)];
  [againButton setBackgroundColor:UIColorWithHexString(@"#41baec")];
  [againButton setTitle:@"TRY SAVING AGAIN" forState:UIControlStateNormal];
  [againButton setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
  [[againButton titleLabel] setFont:[UIFont boldSystemFontOfSize:([againButton titleLabel].font.pointSize - 1)]];
  [[againButton layer] setCornerRadius:8];

  [self ensureWaitCover];
  [waitCover addSubview:decisionModal];
  [decisionModal addSubview:imageView];
  [decisionModal addSubview:errorLabel];
  [decisionModal addSubview:recordButton];
  [decisionModal addSubview:againButton];
  [decisionModal addSubview:orLine];
  [decisionModal addSubview:orLabel];
}

-(void) unloadWaiting {
  [self unloadWaitIndicator];
  [self unloadModal];
  [self unloadWaitCover];
}

-(void) unloadWaitCover {
  if (!waitCover)
    return;

  [waitCover removeFromSuperview];
  waitCover = nil;
}

-(void) unloadWaitIndicator {
  if (!waitIndicator || !waitLabel)
    return;

  if (waitIndicator.isAnimating) {
    [waitIndicator stopAnimating];
    [waitLabel setHidden:YES];
  }

  [waitIndicator removeFromSuperview];
  [waitLabel removeFromSuperview];
  waitIndicator = nil;
  waitLabel = nil;
}

-(void) unloadWaitDescription {
  if (!waitDescription)
    return;
  
  [[waitDescription subviews] makeObjectsPerformSelector:@selector(removeFromSuperview)];
  [waitDescription removeFromSuperview];
  waitDescription = nil;
}

-(void) unloadModal {
  if (!decisionModal)
    return;

  [[decisionModal subviews] makeObjectsPerformSelector:@selector(removeFromSuperview)];
  [decisionModal removeFromSuperview];
  decisionModal = nil;
}

-(NSData *) generateFileUploadData:(NSString *)boundary data:(NSData *)data parameters:(NSDictionary *)parameters {
  NSMutableData* payload = [NSMutableData alloc];
  [parameters enumerateKeysAndObjectsUsingBlock:^(NSString* key, NSObject* value, BOOL* stop) {
    if (![key isEqual:@"filename"] && value && ![value isEqual:[NSNull null]]) {
      [payload appendData:[[NSString stringWithFormat:@"--%@\r\n", boundary] dataUsingEncoding:NSUTF8StringEncoding]];
      [payload appendData:[[NSString stringWithFormat:@"Content-Disposition: form-data; name=\"%@\"\r\n\r\n", key] dataUsingEncoding:NSUTF8StringEncoding]];
      [payload appendData:[[NSString stringWithFormat:@"%@\r\n", value] dataUsingEncoding:NSUTF8StringEncoding]];
    }
  }];
  
  [payload appendData:[[NSString stringWithFormat:@"--%@\r\n", boundary] dataUsingEncoding:NSUTF8StringEncoding]];
  [payload appendData:[[NSString stringWithFormat:@"Content-Disposition: form-data; name=\"upload[file]\"; filename=\"%@\"\r\n", parameters[@"filename"]] dataUsingEncoding:NSUTF8StringEncoding]];
  [payload appendData:[@"Content-Type: application/octet-stream\r\n" dataUsingEncoding:NSUTF8StringEncoding]];
  [payload appendData:[@"Content-Transfer-Encoding: binary\r\n\r\n" dataUsingEncoding:NSUTF8StringEncoding]];
  [payload appendData:data];
  [payload appendData:[@"\r\n" dataUsingEncoding:NSUTF8StringEncoding]];
  [payload appendData:[[NSString stringWithFormat:@"--%@--\r\n", boundary] dataUsingEncoding:NSUTF8StringEncoding]];
  return payload;
}

-(void) continueToTryAgain {
  [self unloadModal];
  [self unloadWaitCover];

  [self ensureWaitCover];
  [self ensureWaitIndicator];
  [self ensureWaitDescription:@"We are saving your video. This may take a moment based on your connection."];
  
  [self uploadVideo];
}

-(void) continueToRecordNew {
  [self unloadModal];
  [self unloadWaitCover];
  
  ControllerAssessmentOverlay* parent = (ControllerAssessmentOverlay*)self.parentViewController;
  [[parent overlayImage] setHidden:NO];
  [[parent timerLabel] setText:@"00.000"];
  [parent retakeVideo:self forMovie:[self.plugin currentVideoUrl]];
}

-(void) continueToNextExercise {
  [self unloadModal];
  [self unloadWaitCover];
  
  AssessmentResult* result = [[AssessmentResult alloc] init];
  [result setCapturedImage:NO];
  [result setCapturedVideo:YES];
  [result setVideoUrl:[self.plugin currentVideoUrl]];
  [self.plugin captured:result];
}

-(void) uploadingFailedFired:(NSTimer *)timer {
  dispatch_async(dispatch_get_main_queue(), ^(void) {
    if (loadingTimer) {
      if ([loadingTimer isValid])
        [loadingTimer invalidate];
      loadingTimer = nil;
    }
    
    [[self.plugin exercise] setVideoUrl:NULL];
    [self.saveInfoView setHidden:YES];
    [self.saveButton setHidden:YES];
    [self.takeButton setHidden:YES];
    [self.retakeButton setHidden:YES];
    
    [self unloadWaitDescription];
    [self unloadWaitIndicator];
    [self ensureDecisionModal];
  });
}

-(void) uploadingFinishFired:(NSTimer *)timer {
  dispatch_async(dispatch_get_main_queue(), ^(void) {
    if (loadingTimer) {
      if ([loadingTimer isValid])
        [loadingTimer invalidate];
      loadingTimer = nil;
    }
    
    [[self.plugin exercise] setVideoUrl:[self.plugin currentVideoUrl]];
    [self.saveInfoView setHidden:YES];
    [self.saveButton setHidden:YES];
    [self.takeButton setHidden:YES];
    [self.retakeButton setHidden:YES];
    
    [self unloadWaitDescription];
    [self unloadWaitIndicator];
    [self ensureSuccessModal];
  });
}

-(void) viewDidLoad {
  [super viewDidLoad];
  [[self.saveInfoView layer] setCornerRadius:8];

  AVPlayer* player = [AVPlayer playerWithURL:[self.plugin currentVideoUrl]];
  AVPlayerItem* playerItem = [player currentItem];
  player.automaticallyWaitsToMinimizeStalling = YES;
  player.actionAtItemEnd = AVPlayerActionAtItemEndPause;
  player.muted = YES;
  
  [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(playerReachedEnd:) name:AVPlayerItemDidPlayToEndTimeNotification object:[player currentItem]];
  [player addObserver:self forKeyPath:@"status" options:0 context:nil];
  [playerItem addObserver:self forKeyPath:@"status" options:0 context:nil];
  
  self.moviePlayer = [[AVPlayerViewController alloc] init];
  [self.moviePlayer setAllowsPictureInPicturePlayback:NO];
  [self.moviePlayer setShowsPlaybackControls:NO];
  [self.moviePlayer setPlayer:player];
  [self.moviePlayer setVideoGravity:AVLayerVideoGravityResizeAspectFill];
  [self.slider setMinimumValue:0.0];
  [self.slider setValue:0.0];
  initializedSeekbar = NO;
}

-(void) viewWillAppear:(BOOL)animated {
  [super viewWillAppear:animated];
  [[UIDevice currentDevice] setValue:[NSNumber numberWithInteger:UIInterfaceOrientationPortrait] forKey:@"orientation"];
  
  dispatch_async(dispatch_get_main_queue(), ^{
    [self.playerView setBackgroundColor:UIColor.blackColor];
    [self.playerView addSubview:self.moviePlayer.view];
    [self.slider setHidden:YES];
    [self.playbackButton setSelected:YES];

    [self initSeekbar];
    [self.saveInfoView setHidden:NO];
    [self.saveButton setHidden:NO];
    [self.retakeButton setHidden:NO];
  });
}

-(void) viewDidLayoutSubviews {
  [super viewDidLayoutSubviews];
    
  [self.playerView layoutIfNeeded];
  self.moviePlayer.view.frame = self.playerView.bounds;
}

-(void) viewWillDisappear:(BOOL)animated {
  AVPlayer* player = [self.moviePlayer player];
  AVPlayerItem* playerItem = [player currentItem];
  [player removeTimeObserver:seekbarObserver];
  [player removeObserver:self forKeyPath:@"status"];
  [playerItem removeObserver:self forKeyPath:@"status"];
  alertController = nil;
  
  [[NSNotificationCenter defaultCenter] removeObserver:self name:AVPlayerItemDidPlayToEndTimeNotification object:nil];
  [super viewWillDisappear:animated];
}

-(void) didReceiveMemoryWarning {
  alertController = [UIAlertController alertControllerWithTitle:@"Unable to Capture Video" message:@"It appears you are running low on memory. Try closing a few applications. Make sure you have enough space to record a movie or video." preferredStyle:UIAlertControllerStyleAlert];
  [alertController addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault handler:^(UIAlertAction* action) {
    [self.plugin failed:@"Low memory warning"];
  }]];

  [self presentViewController:alertController animated:YES completion:nil];
  [super didReceiveMemoryWarning];
}

-(BOOL) prefersStatusBarHidden {
  return YES;
}

-(BOOL) shouldAutorotate {
  return NO;
}

-(UIInterfaceOrientation) preferredInterfaceOrientationForPresentation {
  return UIInterfaceOrientationPortrait;
}

-(UIInterfaceOrientationMask) supportedInterfaceOrientations {
  return UIInterfaceOrientationMaskPortrait;
}

-(UIViewController *) childViewControllerForStatusBarHidden {
  return nil;
}

-(int) getRandomNumber:(int)from to:(int)to {
  return (int)from + arc4random() % (to-from+1);
}

static UIColor* UIColorWithHexString(NSString* hex) {
  unsigned int rgb = 0;
  [[NSScanner scannerWithString:
    [[hex uppercaseString] stringByTrimmingCharactersInSet:
     [[NSCharacterSet characterSetWithCharactersInString:@"0123456789ABCDEF"] invertedSet]]] scanHexInt:&rgb];
  return [UIColor colorWithRed:((CGFloat)((rgb & 0xFF0000) >> 16)) / 255.0
                         green:((CGFloat)((rgb & 0xFF00) >> 8)) / 255.0
                          blue:((CGFloat)(rgb & 0xFF)) / 255.0
                         alpha:1.0];
}

@end
