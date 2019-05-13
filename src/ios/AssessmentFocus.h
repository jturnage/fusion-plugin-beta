
#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

@interface AssessmentFocus : UIView <CAAnimationDelegate> {}

-(id) initWithTouchPoint:(CGPoint)point;
-(BOOL) updateTouchPoint:(CGPoint)point;
-(void) animate;

@end