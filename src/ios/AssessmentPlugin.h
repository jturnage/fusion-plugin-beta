
#import <Cordova/CDV.h>
#import "AssessmentExercise.h"
#import "AssessmentResult.h"
#import "ControllerAssessmentOverlay.h"
#import "ControllerAssessmentReview.h"

@interface AssessmentPlugin : CDVPlugin {
  BOOL hasPendingOperation;
}

@property (nonatomic) CDVInvokedUrlCommand* command;
@property AssessmentExercise* exercise;
@property NSURL* currentVideoUrl;
@property NSURL* uploadEndpointUrl;
@property NSString* apiAuthorize;
@property NSString* apiVersion;
@property BOOL markersEnabled;

-(void) takeVideo:(CDVInvokedUrlCommand*)command;
-(void) cancelled;
-(void) failed:(NSString*)message;
-(void) captured:(AssessmentResult*)result;

@end